package in.govtjobs.web.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** The rate-limit response stays inside the public auth layout. */
@Controller
public class RateLimitController {

    @RequestMapping("/error/rate-limit")
    public String limited(HttpServletRequest request, HttpServletResponse response) {
        Object retry = request.getAttribute("retryAfterSeconds");
        if (retry != null) {
            response.setHeader("Retry-After", String.valueOf(retry));
        }
        response.setStatus(429);
        return "auth/rate-limit";
    }
}
