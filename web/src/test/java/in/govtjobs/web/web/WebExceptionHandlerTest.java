package in.govtjobs.web.web;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.web.store.StoreException;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;
import org.springframework.web.servlet.view.RedirectView;

class WebExceptionHandlerTest {

    @Test
    void storeExceptionNeverFollowsExternalReferer() {
        WebExceptionHandler handler = new WebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://evil.example/phish");
        request.setRequestURI("/desk/1");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = handler.store(new StoreException("VALIDATION", "bad"), request, redirect);
        assertThat(view).isEqualTo("redirect:/dashboard");
        assertThat(view).doesNotContain("evil");
        assertThat(redirect.getFlashAttributes().get("notice")).isEqualTo("bad");
    }

    @Test
    void authStoreExceptionGoesHome() {
        WebExceptionHandler handler = new WebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://evil.example/");
        String view = handler.store(new StoreException("AUTH", "Not signed in"), request, new RedirectAttributesModelMap());
        assertThat(view).isEqualTo("redirect:/");
    }

    @Test
    void oversizeUploadStaysOnTheDeskItem() {
        WebExceptionHandler handler = new WebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/desk/item-9/files/admit");
        SessionFlashMapManager flashes = new SessionFlashMapManager();
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, flashes);
        RedirectView view = handler.uploadTooLarge(
                new MaxUploadSizeExceededException(5L), request, new MockHttpServletResponse());
        assertThat(view.getUrl()).isEqualTo("/desk/item-9");
        assertThat(savedNotice(request)).isEqualTo(WebExceptionHandler.FILE_RULE);
    }

    @Test
    void oversizeUploadOutsideTheDeskGoesToTheDashboard() {
        WebExceptionHandler handler = new WebExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        RedirectView view = handler.uploadTooLarge(
                new MaxUploadSizeExceededException(5L), request, new MockHttpServletResponse());
        assertThat(view.getUrl()).isEqualTo("/dashboard");
        assertThat(savedNotice(request)).isEqualTo(WebExceptionHandler.FILE_RULE);
    }

    @Test
    void container413UsesTheOriginalDeskPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/desk/item-9/files/result");
        request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, new SessionFlashMapManager());
        RedirectView view = new UploadLimitController().tooLarge(request, new MockHttpServletResponse());
        assertThat(view.getUrl()).isEqualTo("/desk/item-9");
        assertThat(savedNotice(request)).isEqualTo(WebExceptionHandler.FILE_RULE);
    }

    @SuppressWarnings("unchecked")
    private static Object savedNotice(MockHttpServletRequest request) {
        Object raw = request.getSession().getAttribute(
                "org.springframework.web.servlet.support.SessionFlashMapManager.FLASH_MAPS");
        List<FlashMap> maps = (List<FlashMap>) raw;
        return maps.get(0).get("notice");
    }
}
