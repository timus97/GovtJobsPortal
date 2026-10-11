package in.govtjobs.web.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.error.ErrorPageRegistrar;
import org.springframework.boot.web.error.ErrorPageRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Tomcat rejects an oversize multipart with HTTP 413 before a controller advice can run.
 * Send that response back to the desk item with the same file rule.
 */
@Controller
class UploadLimitController {

    @RequestMapping("/error/upload")
    public RedirectView tooLarge(HttpServletRequest request, HttpServletResponse response) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri == null ? "" : String.valueOf(uri);
        return WebExceptionHandler.fileTooLarge(request, response, path);
    }
}

@Component
class UploadLimitPages implements ErrorPageRegistrar {
    @Override
    public void registerErrorPages(ErrorPageRegistry registry) {
        registry.addErrorPages(new ErrorPage(HttpStatus.PAYLOAD_TOO_LARGE, "/error/upload"));
    }
}
