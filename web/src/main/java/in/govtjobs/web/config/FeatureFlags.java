package in.govtjobs.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "govtjobs.feature")
public class FeatureFlags {

    private boolean student = true;
    private boolean serverMatch = true;
    private boolean prepare = true;
    private boolean unpublish = true;

    public boolean isStudent() {
        return student;
    }

    public void setStudent(boolean student) {
        this.student = student;
    }

    public boolean isServerMatch() {
        return serverMatch;
    }

    public void setServerMatch(boolean serverMatch) {
        this.serverMatch = serverMatch;
    }

    public boolean isPrepare() {
        return prepare;
    }

    public void setPrepare(boolean prepare) {
        this.prepare = prepare;
    }

    public boolean isUnpublish() {
        return unpublish;
    }

    public void setUnpublish(boolean unpublish) {
        this.unpublish = unpublish;
    }
}
