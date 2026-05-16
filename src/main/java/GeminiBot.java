import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.util.concurrent.ThreadLocalRandom;

public class GeminiBot {

    // Ajuste estas coordenadas conforme sua tela e resolução

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        String basePath = "/Users/walterdovalle/Downloads/livro matematica/";
        robot.delay(4000);

        for (int i = 1; i <= 316; i++) {
            String fileName = String.format("telaris-matematica-7-ano-compress.%03d.png", i);
            System.out.println("Processado: " + fileName);
            File file = new File(basePath + fileName);

            if (!file.exists()) continue;

            // nova conversa
//            robot.keyPress(KeyEvent.VK_META);  // Command
//            robot.keyPress(KeyEvent.VK_SHIFT); // Shift
//            robot.keyPress(KeyEvent.VK_O);
//            robot.keyRelease(KeyEvent.VK_O);
//            robot.keyRelease(KeyEvent.VK_SHIFT);
//            robot.keyRelease(KeyEvent.VK_META);
            click(robot, 98, 180);
            robot.delay(1000);

            String prompt1 = "Melhore a imagem. Não ofereça outras opções no final. Ao baixar, use o mesmo nome de arquivo. " +
                    "Importante: Não faça comentários, apenas procecsse a imagem, pois você é mudo. " +
                    "Não mostre um JSON como resposta. Não mude cores. Use Nano Banana 2.";
            String prompt2 = "Melhore a imagem. Use Nano Banana 2. Não mude cores. Não mostre um JSON como resposta." +
                    "Importante: Não faça comentários, apenas procecsse a imagem, pois você é mudo. " +
                    " Não ofereça outras opções no final. Ao baixar, use o mesmo nome de arquivo. ";
            String prompt3 = "Melhore a imagem. Não mostre um JSON como resposta. Use Nano Banana 2." +
                    "Não ofereça outras opções. Não mude cores. Ao baixar, use o mesmo nome de arquivo. " +
                    "Importante: Não faça comentários, apenas procecsse a imagem  pois você é mudo.";
            int numeroSorteado = ThreadLocalRandom.current().nextInt(1, 4);
            //digitar prompt
            if (numeroSorteado == 1) {
                typeString(robot, prompt1);
            } else if (numeroSorteado == 2) {
                typeString(robot, prompt2);
            } else if (numeroSorteado == 3) {
                typeString(robot, prompt3);
            } else {
                typeString(robot, prompt1);
            }
            robot.delay(400);

            //botao +
            click(robot, 720, 655);
            robot.delay(500);

            //botao enviar arq
            click(robot, 760, 700);
            robot.delay(1000);

            //campo nome
            click(robot, 1280, 250);
            robot.delay(500);
            typeString(robot, fileName);
            robot.delay(500);

            //selecao arquivo
            click(robot, 835, 363);
            robot.delay(50);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.delay(6000);

            //enviar
            click(robot, 1410, 700);
            robot.delay(30000);
            robot.delay(20000);

            //download
            click(robot, 1178, 788);
            robot.delay(35000);
            file.renameTo(new File(basePath + fileName+".processed"));
//            break;
        }
    }

    private static void click(Robot r, int x, int y) {
        r.mouseMove(x, y);
        r.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
        r.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
    }

    private static void typeString1(Robot r, String text) {
        for (char c : text.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (KeyEvent.CHAR_UNDEFINED == keyCode) {
                throw new RuntimeException("Key not found: " + c);
            }
            r.keyPress(keyCode);
            r.keyRelease(keyCode);
        }
    }

    private static void typeString(Robot robot, String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, stringSelection);

        robot.keyPress(KeyEvent.VK_META);
        robot.delay(500);
        robot.keyPress(KeyEvent.VK_V);
        robot.delay(500);
        robot.keyRelease(KeyEvent.VK_V);
        robot.delay(500);
        robot.keyRelease(KeyEvent.VK_META);
        robot.delay(500);
    }
}