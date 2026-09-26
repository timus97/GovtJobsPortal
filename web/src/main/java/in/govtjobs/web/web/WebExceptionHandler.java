package in.govtjobs.web.web;

import in.govtjobs.domain.match.MatchException;
import in.govtjobs.web.store.StoreException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(StoreException.class)
    public String store(StoreException ex, HttpServletRequest request, RedirectAttributes redirect) {
        log.warn("store code={} msg={} path={}", ex.code(), ex.getMessage(), request.getRequestURI());
        redirect.addFlashAttribute("notice", ex.getMessage());
        return "AUTH".equals(ex.code()) ? "redirect:/" : "redirect:/dashboard";
    }

    @ExceptionHandler(MatchException.class)
    public String match(MatchException ex, RedirectAttributes redirect) {
        log.warn("match.invalid_profile errors={}", ex.getErrors());
        redirect.addFlashAttribute("notice", ex.getMessage());
        return "redirect:/profile";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String denied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("http.denied path={}", request.getRequestURI());
        return "redirect:/";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView unhandled(Exception ex, HttpServletRequest request) {
        String rid = MDC.get("requestId");
        log.error("http.unhandled path={} rid={}", request.getRequestURI(), rid, ex);
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("requestId", rid);
        mv.addObject("path", request.getRequestURI());
        return mv;
    }
}
