package in.govtjobs.web.logging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.govtjobs.web.mail.StudentMailService;
import in.govtjobs.web.store.JobStore;
import in.govtjobs.web.store.StudentJsonImporter;
import in.govtjobs.web.store.StudentStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogStartupTest {

    @Test
    void readyImportsThenLogsCatalogAndMail() {
        JobStore jobs = mock(JobStore.class);
        StudentStore students = mock(StudentStore.class);
        StudentMailService mail = mock(StudentMailService.class);
        StudentJsonImporter importer = mock(StudentJsonImporter.class);
        when(jobs.catalogHealth()).thenReturn(Map.of("jobs", 4, "examSeries", 2));
        when(students.backend()).thenReturn("postgres");
        when(mail.provider()).thenReturn("dev");

        new CatalogStartup(jobs, students, mail, importer).onReady();

        verify(importer).importIfEmpty();
        verify(jobs).catalogHealth();
        verify(students).backend();
        verify(mail).provider();
    }
}
