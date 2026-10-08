package in.govtjobs.web.student;

import in.govtjobs.web.store.StoreException;
import in.govtjobs.web.store.StudentStore;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DeskController {

    private final StudentStore students;
    private final CurrentStudent current;

    public DeskController(StudentStore students, CurrentStudent current) {
        this.students = students;
        this.current = current;
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, @RequestParam(required = false) String status, Model model) {
        String id = studentId(principal);
        model.addAttribute("nav", "desk");
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("desk", students.listItems(id, status));
        return "desk/dashboard";
    }

    @PostMapping("/dashboard")
    public String addCustom(
            Principal principal,
            @RequestParam String title,
            @RequestParam(required = false) String board,
            @RequestParam String examDate,
            @RequestParam(required = false) String officialUrl,
            RedirectAttributes redirect) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", "custom");
        body.put("title", title);
        body.put("board", board);
        body.put("examDate", examDate);
        body.put("officialUrl", officialUrl);
        body.put("status", "watching");
        try {
            students.createItem(studentId(principal), body);
        } catch (StoreException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/desk/track")
    public String track(
            Principal principal,
            @RequestParam String kind,
            @RequestParam String refId,
            @RequestParam(defaultValue = "watching") String status,
            RedirectAttributes redirect) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("refId", refId);
        body.put("status", status);
        try {
            students.createItem(studentId(principal), body);
        } catch (StoreException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/desk/{id}")
    public String detail(Principal principal, @PathVariable String id, Model model) {
        Map<String, Object> item = students.getItem(studentId(principal), id);
        if (item == null) return "redirect:/dashboard";
        model.addAttribute("nav", "desk");
        model.addAttribute("item", item);
        return "desk/detail";
    }

    @PostMapping("/desk/{id}")
    public String update(
            Principal principal,
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String examDate,
            @RequestParam(required = false) String lastDate,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String action,
            RedirectAttributes redirect) {
        String sid = studentId(principal);
        if ("delete".equals(action)) {
            students.deleteItem(sid, id);
            return "redirect:/dashboard";
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        if (status != null) patch.put("status", status);
        if (examDate != null) patch.put("examDate", examDate);
        if (lastDate != null) patch.put("lastDate", lastDate);
        if (notes != null) patch.put("notes", notes);
        try {
            students.patchItem(sid, id, patch);
        } catch (StoreException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/desk/" + id;
    }

    @PostMapping("/desk/{id}/files/{kind}")
    public String upload(
            Principal principal,
            @PathVariable String id,
            @PathVariable String kind,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirect)
            throws Exception {
        try {
            students.saveFile(studentId(principal), id, kind, file.getOriginalFilename(), file.getBytes());
        } catch (StoreException e) {
            redirect.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/desk/" + id;
    }

    @GetMapping("/desk/{id}/files/{kind}")
    public ResponseEntity<Resource> download(
            Principal principal, @PathVariable String id, @PathVariable String kind) {
        var path = students.resolveFile(studentId(principal), id, kind);
        if (path == null || !path.toFile().isFile()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> meta = students.fileMeta(studentId(principal), id, kind);
        String downloadName = path.getFileName().toString();
        if (meta != null && meta.get("originalName") != null) {
            downloadName = String.valueOf(meta.get("originalName")).replace("\"", "");
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (meta != null && meta.get("mime") != null) {
            try {
                type = MediaType.parseMediaType(String.valueOf(meta.get("mime")));
            } catch (Exception ignored) {
                type = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(type)
                .body(new FileSystemResource(path));
    }

    @PostMapping("/desk/{id}/files/{kind}/delete")
    public String deleteFile(Principal principal, @PathVariable String id, @PathVariable String kind) {
        students.deleteFile(studentId(principal), id, kind);
        return "redirect:/desk/" + id;
    }

    private String studentId(Principal principal) {
        return current.requireId(principal);
    }
}
