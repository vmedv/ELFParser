import java.io.IOException;

public class IncorrectInputException extends IOException {
    private String err;
    public IncorrectInputException(String err) {
        this.err = err;
    }
    @Override
    public String getMessage() {
        return "Input file is not a correct RISC-V 32bit little endian ELF file: " + err;
    }
}