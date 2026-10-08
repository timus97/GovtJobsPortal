package in.govtjobs.web.web;

import static org.assertj.core.api.Assertions.assertThat;

import in.govtjobs.web.store.StoreException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

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
}
