package in.govtjobs.web.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts Node-style {@code postgres://user:pass@host:5432/db} as {@code STUDENT_DATABASE_URL}
 * and maps it onto Spring {@code spring.datasource.*}.
 */
public class StudentDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String springUrl = environment.getProperty("spring.datasource.url");
        if (springUrl != null && springUrl.startsWith("jdbc:tc:")) {
            return;
        }
        String raw = environment.getProperty("STUDENT_DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }
        Map<String, Object> props = new HashMap<>();
        if (raw.startsWith("jdbc:")) {
            props.put("spring.datasource.url", raw);
        } else if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
            try {
                URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "http://"));
                String userInfo = uri.getUserInfo();
                String user = "govtjobs";
                String pass = "govtjobs";
                if (userInfo != null && !userInfo.isBlank()) {
                    int colon = userInfo.indexOf(':');
                    if (colon >= 0) {
                        user = userInfo.substring(0, colon);
                        pass = userInfo.substring(colon + 1);
                    } else {
                        user = userInfo;
                    }
                }
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String db = uri.getPath() == null || uri.getPath().isBlank() ? "/govtjobs_students" : uri.getPath();
                String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + db;
                props.put("spring.datasource.url", jdbc);
                props.put("spring.datasource.username", user);
                props.put("spring.datasource.password", pass);
            } catch (RuntimeException ignored) {
                return;
            }
        }
        if (!props.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("student-database-url", props));
        }
    }

}
