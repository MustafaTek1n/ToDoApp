import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Console-based To-Do Application
 * - Add / List / Complete / Delete / Edit / Search
 * - Filter pending/completed
 * - Clear completed
 * - Auto-save & auto-load from tasks.dat (Java serialization, stdlib only)
 *
 * How to run:
 *   javac ToDoApp.java && java ToDoApp
 */
public class ToDoApp {

    // ====== Models ======
    public enum Priority implements Serializable {
        LOW, MEDIUM, HIGH;
        public static Priority fromInt(int n) {
            switch (n) {
                case 1: return LOW;
                case 2: return MEDIUM;
                case 3: return HIGH;
                default: return MEDIUM;
            }
        }
    }

    public static class Task implements Serializable {
        private static final long serialVersionUID = 1L;

        private int id;
        private String description;
        private boolean done;
        private Priority priority;
        private LocalDateTime createdAt;
        private LocalDate dueDate; // nullable

        public Task(int id, String description, Priority priority, LocalDate dueDate) {
            this.id = id;
            this.description = description;
            this.priority = priority == null ? Priority.MEDIUM : priority;
            this.dueDate = dueDate;
            this.createdAt = LocalDateTime.now();
            this.done = false;
        }

        public int getId() { return id; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isDone() { return done; }
        public void markDone() { this.done = true; }
        public void markUndone() { this.done = false; }

        public Priority getPriority() { return priority; }
        public void setPriority(Priority p) { this.priority = p; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

        @Override
        public String toString() {
            String due = (dueDate == null) ? "-" : dueDate.toString();
            String status = done ? "[X]" : "[ ]";
            return String.format("%s #%d | %s | priority:%s | due:%s | created:%s",
                    status, id, description, priority,
                    due,
                    createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
    }

    // ====== App State ======
    private final Scanner scanner = new Scanner(System.in);
    private final List<Task> tasks = new ArrayList<>();
    private int nextId = 1;
    private static final String SAVE_FILE = "tasks.dat";

    // ====== Entry Point ======
    public static void main(String[] args) {
        ToDoApp app = new ToDoApp();
        app.load();
        app.run();
        app.save();
        System.out.println("\nGörüşürüz! :)");
    }

    // ====== Main Loop ======
    private void run() {
        while (true) {
            printMenu();
            int choice = readInt("Seçiminiz: ");
            switch (choice) {
                case 1: addTaskUI(); autoSave(); break;
                case 2: listTasksUI(tasks); break;
                case 3: completeTaskUI(); autoSave(); break;
                case 4: deleteTaskUI(); autoSave(); break;
                case 5: editTaskUI(); autoSave(); break;
                case 6: searchTaskUI(); break;
                case 7: listPendingUI(); break;
                case 8: listCompletedUI(); break;
                case 9: clearCompletedUI(); autoSave(); break;
                case 0: return; // exit
                default:
                    System.out.println("Geçersiz seçim. Tekrar deneyiniz.");
            }
            pause();
        }
    }

    // ====== UI Methods ======
    private void printMenu() {
        System.out.println("\n==== TO-DO APP ====");
        System.out.println("1) Görev Ekle");
        System.out.println("2) Görevleri Listele");
        System.out.println("3) Görevi Tamamla / Geri Al");
        System.out.println("4) Görevi Sil");
        System.out.println("5) Görevi Düzenle (açıklama/öncelik/son tarih)");
        System.out.println("6) Ara (kelime)");
        System.out.println("7) Sadece Bekleyenleri Listele");
        System.out.println("8) Sadece Tamamlananları Listele");
        System.out.println("9) Tüm Tamamlananları Temizle");
        System.out.println("0) Çıkış ve Kaydet");
        System.out.println("===================");
    }

    private void addTaskUI() {
        System.out.println("\n--- Görev Ekle ---");
        String desc = readLine("Açıklama: ");
        Priority prio = readPriority();
        LocalDate due = readDueDateOptional();

        Task t = new Task(nextId++, desc, prio, due);
        tasks.add(t);
        System.out.println("Eklendi: " + t);
    }

    private void listTasksUI(List<Task> list) {
        if (list.isEmpty()) {
            System.out.println("Hiç görev yok. Kahveni al, hedef koy! ☕");
            return;
        }
        List<Task> sorted = new ArrayList<>(list);
        // Sort: not done first, then by priority HIGH->LOW, then by due date, then by id
        sorted.sort((a,b) -> {
            if (a.isDone() != b.isDone()) return a.isDone() ? 1 : -1;
            int pr = b.getPriority().ordinal() - a.getPriority().ordinal();
            if (pr != 0) return pr;
            if (a.getDueDate() == null && b.getDueDate() != null) return 1;
            if (a.getDueDate() != null && b.getDueDate() == null) return -1;
            if (a.getDueDate() != null && b.getDueDate() != null) {
                int cmp = a.getDueDate().compareTo(b.getDueDate());
                if (cmp != 0) return cmp;
            }
            return Integer.compare(a.getId(), b.getId());
        });

        System.out.println("\n--- Görevler ---");
        for (Task t : sorted) {
            System.out.println(t);
        }
    }

    private void completeTaskUI() {
        System.out.println("\n--- Görevi Tamamla / Geri Al ---");
        int id = readInt("Görev ID: ");
        Task t = findById(id);
        if (t == null) {
            System.out.println("ID bulunamadı.");
            return;
        }
        if (t.isDone()) {
            t.markUndone();
            System.out.println("Geri alındı: " + t);
        } else {
            t.markDone();
            System.out.println("Tamamlandı: " + t);
        }
    }

    private void deleteTaskUI() {
        System.out.println("\n--- Görevi Sil ---");
        int id = readInt("Görev ID: ");
        Task t = findById(id);
        if (t == null) {
            System.out.println("ID bulunamadı.");
            return;
        }
        tasks.remove(t);
        System.out.println("Silindi: " + t);
    }

    private void editTaskUI() {
        System.out.println("\n--- Görevi Düzenle ---");
        int id = readInt("Görev ID: ");
        Task t = findById(id);
        if (t == null) {
            System.out.println("ID bulunamadı.");
            return;
        }
        System.out.println("Seçilen: " + t);
        System.out.println("1) Açıklamayı değiştir");
        System.out.println("2) Önceliği değiştir");
        System.out.println("3) Son tarihi değiştir / kaldır");
        System.out.println("0) İptal");
        int ch = readInt("Seçiminiz: ");
        switch (ch) {
            case 1:
                String nd = readLine("Yeni açıklama: ");
                t.setDescription(nd);
                System.out.println("Güncellendi: " + t);
                break;
            case 2:
                Priority np = readPriority();
                t.setPriority(np);
                System.out.println("Güncellendi: " + t);
                break;
            case 3:
                LocalDate ndue = readDueDateOptionalAllowClear();
                t.setDueDate(ndue);
                System.out.println("Güncellendi: " + t);
                break;
            default:
                System.out.println("İptal.");
        }
    }

    private void searchTaskUI() {
        System.out.println("\n--- Ara ---");
        String q = readLine("Kelime: ").toLowerCase(Locale.ROOT);
        List<Task> found = new ArrayList<>();
        for (Task t : tasks) {
            if (t.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                found.add(t);
            }
        }
        listTasksUI(found);
    }

    private void listPendingUI() {
        List<Task> pending = new ArrayList<>();
        for (Task t : tasks) if (!t.isDone()) pending.add(t);
        listTasksUI(pending);
    }

    private void listCompletedUI() {
        List<Task> done = new ArrayList<>();
        for (Task t : tasks) if (t.isDone()) done.add(t);
        listTasksUI(done);
    }

    private void clearCompletedUI() {
        int before = tasks.size();
        tasks.removeIf(Task::isDone);
        int removed = before - tasks.size();
        System.out.println(removed + " tamamlanan görev silindi.");
    }

    // ====== Helpers ======
    private Task findById(int id) {
        for (Task t : tasks) if (t.getId() == id) return t;
        return null;
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Lütfen sayı gir.");
            }
        }
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    private void pause() {
        System.out.print("Devam etmek için Enter... ");
        scanner.nextLine();
    }

    private Priority readPriority() {
        System.out.println("Öncelik: 1) LOW  2) MEDIUM  3) HIGH");
        int p = readInt("Seçim (1-3): ");
        if (p < 1 || p > 3) p = 2;
        return Priority.fromInt(p);
    }

    private LocalDate readDueDateOptional() {
        System.out.println("Son tarih formatı: YYYY-MM-DD (boş geçip Enter dersen opsiyonel)");
        String s = readLine("Son tarih: ").trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            System.out.println("Tarih anlaşılamadı, boş bıraktım.");
            return null;
        }
    }

    private LocalDate readDueDateOptionalAllowClear() {
        System.out.println("Son tarih formatı: YYYY-MM-DD, ya da 'clear' yazıp kaldır");
        String s = readLine("Son tarih: ").trim();
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("clear")) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            System.out.println("Tarih anlaşılamadı, boş bıraktım.");
            return null;
        }
    }

    // ====== Persistence ======
    private void autoSave() {
        try { save(); } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void load() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = ois.readObject();
            if (obj instanceof List) {
                tasks.clear();
                tasks.addAll((List<Task>) obj);
                // restore nextId
                for (Task t : tasks) nextId = Math.max(nextId, t.getId() + 1);
                System.out.println(tasks.size() + " görev yüklendi.");
            }
        } catch (Exception e) {
            System.out.println("Kaydı yüklerken sorun: " + e.getMessage());
        }
    }

    private void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            oos.writeObject(tasks);
            oos.flush();
            // Not strictly needed to flush but good habit
        } catch (Exception e) {
            System.out.println("Kaydederken hata: " + e.getMessage());
        }
    }
}
