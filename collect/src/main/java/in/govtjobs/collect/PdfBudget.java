package in.govtjobs.collect;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Notice and calendar PDFs share one per-source cap. A daily run also shares one run-wide cap.
 * A download that throws does not keep its slot.
 */
final class PdfBudget {

    private int left;
    private final AtomicInteger runLeft;

    private PdfBudget(int perSource, AtomicInteger runLeft) {
        this.left = perSource;
        this.runLeft = runLeft;
    }

    static PdfBudget perSource(int perSource) {
        return new PdfBudget(perSource, null);
    }

    static PdfBudget shared(int perSource, AtomicInteger runLeft) {
        return new PdfBudget(perSource, runLeft);
    }

    boolean tryAcquire() {
        if (left <= 0) {
            return false;
        }
        if (runLeft != null) {
            while (true) {
                int current = runLeft.get();
                if (current <= 0) {
                    return false;
                }
                if (runLeft.compareAndSet(current, current - 1)) {
                    left--;
                    return true;
                }
            }
        }
        left--;
        return true;
    }

    void release() {
        left++;
        if (runLeft != null) {
            runLeft.incrementAndGet();
        }
    }
}
