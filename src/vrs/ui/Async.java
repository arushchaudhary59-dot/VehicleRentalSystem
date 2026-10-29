package vrs.ui;

import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import vrs.db.DbErrors;
import vrs.ui.comp.Toast;

/**
 * Runs database work on one background thread so the window never freezes,
 * then hands the result back to the Swing thread.
 */
public final class Async {
    private Async() {}

    private static final ExecutorService DB_THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-worker");
        t.setDaemon(true);
        return t;
    });

    @FunctionalInterface
    public interface Task<T> { T call() throws Exception; }

    @FunctionalInterface
    public interface Action { void run() throws Exception; }

    public static <T> void run(Task<T> task, Consumer<T> onSuccess) {
        run(task, onSuccess, e -> Toast.error(DbErrors.friendly(e)));
    }

    public static <T> void run(Task<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        DB_THREAD.submit(() -> {
            try {
                T result = task.call();
                SwingUtilities.invokeLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                if (!(t instanceof IllegalArgumentException)) t.printStackTrace();
                SwingUtilities.invokeLater(() -> onError.accept(t));
            }
        });
    }

    /** For actions without a result. */
    public static void act(Action action, Runnable onSuccess, Consumer<Throwable> onError) {
        run(() -> { action.run(); return Boolean.TRUE; }, ok -> onSuccess.run(), onError);
    }

    public static void act(Action action, Runnable onSuccess) {
        act(action, onSuccess, e -> Toast.error(DbErrors.friendly(e)));
    }
}
