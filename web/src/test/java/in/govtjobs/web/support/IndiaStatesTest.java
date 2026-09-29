package in.govtjobs.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IndiaStatesTest {

    @Test
    void everyStateCodeAndLabelIsPresent() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("AN", "Andaman and Nicobar Islands"),
                Map.entry("AP", "Andhra Pradesh"),
                Map.entry("AR", "Arunachal Pradesh"),
                Map.entry("AS", "Assam"),
                Map.entry("BR", "Bihar"),
                Map.entry("CH", "Chandigarh"),
                Map.entry("CT", "Chhattisgarh"),
                Map.entry("DH", "Dadra and Nagar Haveli and Daman and Diu"),
                Map.entry("DL", "Delhi"),
                Map.entry("GA", "Goa"),
                Map.entry("GJ", "Gujarat"),
                Map.entry("HR", "Haryana"),
                Map.entry("HP", "Himachal Pradesh"),
                Map.entry("JK", "Jammu and Kashmir"),
                Map.entry("JH", "Jharkhand"),
                Map.entry("KA", "Karnataka"),
                Map.entry("KL", "Kerala"),
                Map.entry("LA", "Ladakh"),
                Map.entry("LD", "Lakshadweep"),
                Map.entry("MP", "Madhya Pradesh"),
                Map.entry("MH", "Maharashtra"),
                Map.entry("MN", "Manipur"),
                Map.entry("ML", "Meghalaya"),
                Map.entry("MZ", "Mizoram"),
                Map.entry("NL", "Nagaland"),
                Map.entry("OR", "Odisha"),
                Map.entry("PY", "Puducherry"),
                Map.entry("PB", "Punjab"),
                Map.entry("RJ", "Rajasthan"),
                Map.entry("SK", "Sikkim"),
                Map.entry("TN", "Tamil Nadu"),
                Map.entry("TG", "Telangana"),
                Map.entry("TR", "Tripura"),
                Map.entry("UP", "Uttar Pradesh"),
                Map.entry("UK", "Uttarakhand"),
                Map.entry("WB", "West Bengal"));
        assertThat(IndiaStates.STATES).containsExactlyEntriesOf(expected);
    }

    @Test
    void unknownCodeIsAbsentAndMapIsUnmodifiable() throws Exception {
        assertThat(IndiaStates.STATES.get("ZZ")).isNull();
        assertThat(IndiaStates.STATES.get("an")).isNull();
        assertThatThrownBy(() -> IndiaStates.STATES.put("ZZ", "Nowhere"))
                .isInstanceOf(UnsupportedOperationException.class);
        Constructor<IndiaStates> ctor = IndiaStates.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
