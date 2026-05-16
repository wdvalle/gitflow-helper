import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;

public class MonitorGlobal implements NativeMouseListener, NativeKeyListener {

    public static void main(String[] args) {
        try {
            // 1. Registrar o Hook Global
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            System.err.println("Erro ao registrar o hook: " + ex.getMessage());
            System.exit(1);
        }

        MonitorGlobal monitor = new MonitorGlobal();
        
        // 2. Adicionar os ouvintes (Listeners)
        GlobalScreen.addNativeMouseListener(monitor);
        GlobalScreen.addNativeKeyListener(monitor);
        
        System.out.println("Monitoramento iniciado. Pressione Ctrl+Q para sair.");
    }

    // --- Monitoramento do Mouse ---
    @Override
    public void nativeMouseClicked(NativeMouseEvent e) {
        System.out.println("Clique Global: X=" + e.getX() + ", Y=" + e.getY());
    }

    // --- Atalho para Fechar (Ctrl + Q) ---
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // Verifica se Ctrl está pressionado e a tecla é Q
        if ((e.getModifiers() & NativeKeyEvent.CTRL_L_MASK) != 0 && e.getKeyCode() == NativeKeyEvent.VC_Q) {
            System.out.println("Atalho Ctrl+Q detectado. Encerrando...");
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException ex) {
                ex.printStackTrace();
            }
            System.exit(0);
        }
    }

    // Métodos obrigatórios da interface que não usaremos
    public void nativeMousePressed(NativeMouseEvent e) {}
    public void nativeMouseReleased(NativeMouseEvent e) {}
    public void nativeKeyReleased(NativeKeyEvent e) {}
    public void nativeKeyTyped(NativeKeyEvent e) {}
}