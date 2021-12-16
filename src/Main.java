import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Incorrect arguments");
            return;
        }
        try {
            ParseELF p = new ParseELF(args[0], args[1]);
            p.parse();
            p.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
