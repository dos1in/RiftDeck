import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import java.lang.reflect.Method;

/** Shell-only diagnostic: run with app_process, never package in the application. */
public final class NavigationInput {
    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        int interval = args.length > 1 ? Integer.parseInt(args[1]) : 145;
        if (count < 1 || count > 500 || interval < 50 || interval > 2000) {
            throw new IllegalArgumentException("count: 1..500; interval: 50..2000 ms");
        }
        Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        Method inject = managerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
        System.out.println("READY: focus a game row; starting in 3 seconds");
        SystemClock.sleep(3000);
        for (int direction : new int[]{KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP}) {
            for (int i = 0; i < count; i++) {
                long now = SystemClock.uptimeMillis();
                for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                    KeyEvent event = new KeyEvent(now, SystemClock.uptimeMillis(), action,
                            direction, 0, 0, -1, 0, 0, InputDevice.SOURCE_DPAD);
                    if (!Boolean.TRUE.equals(inject.invoke(manager, event, 2))) {
                        throw new IllegalStateException("Input injection failed");
                    }
                }
                SystemClock.sleep(interval);
            }
        }
        System.out.println("COMPLETE: " + (count * 2) + " directional presses");
    }
}
