package in.govtjobs.web.support;

import java.util.LinkedHashMap;
import java.util.Map;

public final class IndiaStates {

    private IndiaStates() {}

    public static final Map<String, String> STATES = states();

    private static Map<String, String> states() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("AN", "Andaman and Nicobar Islands");
        m.put("AP", "Andhra Pradesh");
        m.put("AR", "Arunachal Pradesh");
        m.put("AS", "Assam");
        m.put("BR", "Bihar");
        m.put("CH", "Chandigarh");
        m.put("CT", "Chhattisgarh");
        m.put("DH", "Dadra and Nagar Haveli and Daman and Diu");
        m.put("DL", "Delhi");
        m.put("GA", "Goa");
        m.put("GJ", "Gujarat");
        m.put("HR", "Haryana");
        m.put("HP", "Himachal Pradesh");
        m.put("JK", "Jammu and Kashmir");
        m.put("JH", "Jharkhand");
        m.put("KA", "Karnataka");
        m.put("KL", "Kerala");
        m.put("LA", "Ladakh");
        m.put("LD", "Lakshadweep");
        m.put("MP", "Madhya Pradesh");
        m.put("MH", "Maharashtra");
        m.put("MN", "Manipur");
        m.put("ML", "Meghalaya");
        m.put("MZ", "Mizoram");
        m.put("NL", "Nagaland");
        m.put("OR", "Odisha");
        m.put("PY", "Puducherry");
        m.put("PB", "Punjab");
        m.put("RJ", "Rajasthan");
        m.put("SK", "Sikkim");
        m.put("TN", "Tamil Nadu");
        m.put("TG", "Telangana");
        m.put("TR", "Tripura");
        m.put("UP", "Uttar Pradesh");
        m.put("UK", "Uttarakhand");
        m.put("WB", "West Bengal");
        return Map.copyOf(m);
    }
}
