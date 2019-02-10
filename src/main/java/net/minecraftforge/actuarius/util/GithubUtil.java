package net.minecraftforge.actuarius.util;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpConnector;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.minecraftforge.actuarius.Main;
import net.minecraftforge.actuarius.util.json.InstallationResponse;
import reactor.util.annotation.Nullable;

public class GithubUtil {
    
    private static final ObjectMapper PARSER = new ObjectMapper();
        
    private static @Nullable String jwt;
    @SuppressWarnings("null")
    private static Instant jwtExpiry = Instant.EPOCH;

    private static @Nullable String token;
    @SuppressWarnings("null")
    private static Instant tokenExpiry = Instant.EPOCH;
    
    private static @Nullable GitHub unauthorizedClient;

    public static synchronized GitHub getUnauthorizedClient() {
        try {
            if (unauthorizedClient == null) {
                unauthorizedClient = new GitHubBuilder().build();
            }
            return unauthorizedClient;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @SuppressWarnings("null")
    public static Optional<String> defaultInstallation() {
        return Optional.ofNullable(Main.cfg.github.defaultInstallation);
    }
    
    public static boolean forceDefault() {
        return Main.cfg.github.forceDefault;
    }
    
    @SuppressWarnings("null")
    static GitHub getClient(final int installation) {
        try {
            return new GitHubBuilder().withConnector(new HttpConnector() {

                @Override
                public HttpURLConnection connect(URL url) throws IOException {
                    HttpURLConnection ret = (HttpURLConnection) url.openConnection();

                    ret.setRequestProperty("Authorization", "token " + getToken(installation));
                    ret.setRequestProperty("Accept", "application/vnd.github.machine-man-preview+json");
                    return ret;
                }
            }).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    static InstallationResponse getInstallation(String org) throws IOException {
        InputStream res = getAuthenticatedConnection("/orgs/" + org + "/installation").getInputStream();
        return PARSER.readValue(res, InstallationResponse.class);
    }
    
    static InstallationResponse getInstallation(String owner, String repo) throws IOException {
        InputStream res = getAuthenticatedConnection("/repos/" + owner + "/" + repo + "/installation").getInputStream();
        return PARSER.readValue(res, InstallationResponse.class);
    }
    
    static InstallationResponse[] getInstallations() throws IOException {
        InputStream res = getAuthenticatedConnection("/app/installations").getInputStream();        
        return PARSER.readValue(res, InstallationResponse[].class);
    }
    
    static synchronized String getToken(int installation) {

        if (token == null || tokenExpiry.isBefore(Instant.now())) {

            try {
                
                // Update the JWT to re-authenticate the app
                URLConnection jwtPingRequest = getAuthenticatedConnection("/app");
                jwtPingRequest.getInputStream().close();
                
                // Request a new token with this authenticated JWT
                HttpURLConnection tokenRequest = (HttpURLConnection) getAuthenticatedConnection("/installations/" + installation + "/access_tokens");
                tokenRequest.setRequestMethod("POST");

                // Read the result
                JsonNode jsonObject = PARSER.readTree(tokenRequest.getInputStream());

                // Note the expiry so re-auth is done as little as possible
                token = jsonObject.get("token").asText();
                tokenExpiry = Instant.parse(jsonObject.get("expires_at").asText());

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        return token;
    }
    
    static URLConnection getAuthenticatedConnection(String url) throws IOException {
        URLConnection con = new URL("https://api.github.com" + url).openConnection();
        
        con.setRequestProperty("Authorization", "Bearer " + getJWT());
        con.setRequestProperty("Accept", "application/vnd.github.machine-man-preview+json");
        return con;
    }
    
    @SuppressWarnings("null")
    static String getJWT() {
        
        if (jwt == null || jwtExpiry.isBefore(Instant.now())) {
            RSAPrivateKey key = getPrivateKey(Main.cfg.github.privateKey);
    
            Algorithm algorithm = Algorithm.RSA256(null, key);
            Date now = new Date();
            
            // Use 9 minutes here, because 10 minutes is the limit and 
            // may be too long if local time is ahead of server time.
            Instant.now().plus(9, ChronoUnit.MINUTES);
            Date expiry = new Date(now.getTime() + TimeUnit.MINUTES.toMillis(9));
            jwtExpiry = expiry.toInstant();
    
            jwt = JWT.create()
                    .withIssuer(String.valueOf(Main.cfg.github.appId))
                    .withIssuedAt(now)
                    .withExpiresAt(expiry) 
                    .sign(algorithm);
        }
        
        return jwt;
    }
    
    static RSAPrivateKey getPrivateKey(String pemFile) {
        try (PemReader reader = new PemReader(new FileReader(pemFile))) {
            PemObject pem = reader.readPemObject();
        
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pem.getContent());
            // "BC" forces the BouncyCastle version, which is required when using the BouncyCastle PemObject
            KeyFactory kf = KeyFactory.getInstance("RSA", "BC");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }
}
