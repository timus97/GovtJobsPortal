package in.govtjobs.web.logging;

import in.govtjobs.web.mail.StudentMailService;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.StudentJsonImporter;
import in.govtjobs.web.store.StudentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CatalogStartup {

    private static final Logger log = LoggerFactory.getLogger(CatalogStartup.class);

    private final JobStore jobs;
    private final StudentStore students;
    private final StudentMailService mail;
    private final StudentJsonImporter importer;

    public CatalogStartup(
            JobStore jobs, StudentStore students, StudentMailService mail, StudentJsonImporter importer) {
        this.jobs = jobs;
        this.students = students;
        this.mail = mail;
        this.importer = importer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        importer.importIfEmpty();
        var catalog = jobs.catalogHealth();
        log.info(
                "boot.ready jobs={} series={} studentBackend={} mail={}",
                catalog.get("jobs"),
                catalog.get("examSeries"),
                students.backend(),
                mail.provider());
    }
}
