package in.govtjobs.web.web;

import in.govtjobs.domain.match.MatchException;
import in.govtjobs.web.store.StoreException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

@ControllerAdvice
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);
    private static final Pattern DESK_FILE =
            Pattern.compile("^/desk/([A-Za-z0-9._-]+)/files/(admit|result)$");
    static final String FILE_RULE = "PDF, JPEG, or PNG. 5 MB at most.";

    @ExceptionHandler(StoreException.class)
    public String store(StoreException ex, HttpServletRequest request, RedirectAttributes redirect) {
        log.warn("store code={} msg={} path={}", ex.code(), ex.getMessage(), request.getRequestURI());
        redirect.addFlashAttribute("notice", ex.getMessage());
        return "AUTH".equals(ex.code()) ? "redirect:/" : "redirect:/dashboard";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public RedirectView uploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request, HttpServletResponse response) {
        return fileTooLarge(request, response, request.getRequestURI());
    }

    static RedirectView fileTooLarge(HttpServletRequest request, HttpServletResponse response, String path) {
        log.warn("desk.file_too_large path={}", path);
        String target = "/dashboard";
        Matcher match = DESK_FILE.matcher(path == null ? "" : path);
        if (match.matches()) {
            target = "/desk/" + match.group(1);
        }
        FlashMap flash = new FlashMap();
        flash.put("notice", FILE_RULE);
        flash.setTargetRequestPath(target);
        FlashMapManager manager = RequestContextUtils.getFlashMapManager(request);
        if (manager != null) {
            manager.saveOutputFlashMap(flash, request, response);
        }
        return new RedirectView(target);
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
