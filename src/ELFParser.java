import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ELFParser {
    private int fileType;
    private int entryPoint;
    private int startProgramHeaderTable;
    private int startSectionHeaderTable;
    private int flags;
    private int programHeaderSize;
    private int numberOfEntriesProgramHeaderTable;
    private int sectionHeaderSize;
    private int numberOfEntriesSectionHeaderTable;
    private int stringTable;
    private final Map<String, Integer> sectionNames = new HashMap<>();
//    private final List<Section> sectionList = new ArrayList<>();

    private String strtab;

    private long globalPosition = 0;
    private InputStream input;
    private final String inputFileName;

    public ELFParser(String inputFileName) throws IOException {
        input = new FileInputStream(inputFileName);
        this.inputFileName = inputFileName;
    }
    public void parseELFHeader() throws IOException {
        byte[] mag = new byte[4];
        int c = input.read(mag);
        globalPosition += c;
        if (c != 4 || mag[0] != 0x7f || mag[1] != 0x45 || mag[2] != 0x4c || mag[3] != 0x46) {
            throw new IncorrectInputException("not an ELF-file");
        }
        if (input.read() != 1) {
            throw new IncorrectInputException("not a 32bit format");
        }
        globalPosition++;
        if (input.read() != 1) {
            throw new IncorrectInputException("not a little endian");
        }
        globalPosition++;
        if (input.read() != 1) {
            throw new IncorrectInputException("invalid version");
        }
        globalPosition++;
        for (int i = 8; i < 17; i++) {
            //padding bytes
            input.read();
            globalPosition++;
        }
        fileType = input.read();
        globalPosition++;
        if (0 <= fileType && fileType <= 4) {
            input.read();
            globalPosition++;
        } else if (fileType == 0xff) {
            fileType <<= 8;
            fileType += input.read();
        } else {
            throw new IncorrectInputException("wrong file type");
        }
        if (input.read() != 0xf3) {
            throw new IncorrectInputException("not RISC-V ISA ELF file");
        }
        globalPosition++;
        if (input.read() != 0) {
            throw new IncorrectInputException("not RISC-V ISA ELF file");
        }
        globalPosition++;
        if (parse32bit() != (1)) {
            throw new IncorrectInputException("invalid version");
        }
        entryPoint = parse32bit();
        startProgramHeaderTable = parse32bit();
        startSectionHeaderTable = parse32bit();
        System.err.println(startSectionHeaderTable);
        flags = parse32bit();
        if (parseNByteValue(2) != 52) {
            throw new IncorrectInputException("corrupted or not correct ELF file");
        }
        programHeaderSize = parseNByteValue(2);
        numberOfEntriesProgramHeaderTable = parseNByteValue(2);
        sectionHeaderSize = parseNByteValue(2);
        numberOfEntriesSectionHeaderTable = parseNByteValue(2);
        stringTable = parseNByteValue(2);
    }

    public void parseSectionTable() throws IOException {
        while (globalPosition < startSectionHeaderTable) {
            input.read();
            globalPosition++;
        }
//        for (int i = 0; i < numberOfEntriesSectionHeaderTable; i++) {
//            int[] args = new int[10];
//            for (int j = 0; j < args.length; j++) {
//                args[j] = parse32bit();
//            }
//            sectionList.add(new Section(args));
//        }
//        if (sectionList.get(stringTable).getOffset() < globalPosition) {
//            input = new FileInputStream(inputFileName);
//            globalPosition = 0;
//        }
//        while (globalPosition < sectionList.get(stringTable).getOffset()) {
//            input.read();
//            globalPosition++;
//        }
//        StringBuilder buildName = new StringBuilder();
//        for (int i = 0; i < sectionList.get(stringTable).getSize(); i++) {
//            int t = input.read();
//            buildName.append((char)t);
//            if (".symtab".equals(buildName.toString())) {
//                sectionNames.put(".symtab", i - buildName.length() + 1);
//                buildName = new StringBuilder();
//            } else if (".text".equals(buildName.toString())) {
//                sectionNames.put(".text", i - buildName.length() + 1);
//                buildName = new StringBuilder();
//            } else if (".strtab".equals(buildName.toString())) {
//                sectionNames.put(".strtab", i - buildName.length() + 1);
//                buildName = new StringBuilder();
//            } else if (!".symtab".startsWith(buildName.toString()) &&
//                    !".text".startsWith(buildName.toString()) &&
//                    !".strtab".startsWith(buildName.toString()))
//                buildName = new StringBuilder();
//        }
//        for (Section sec : sectionList) {
//            if (sec.getName() == sectionNames.get(".strtab")) {
//                strtab = parseStrTab(sec.getOffset(), sec.getSize());
//            }
//        }
//        for (Section sec : sectionList) {
//            if (sec.getName() == sectionNames.get(".symtab")) {
//                parseSymTab(sec.getOffset(), sec.getSize());
//            }
//        }
//        for (Section sec : sectionList) {
//            if (sec.getName() == sectionNames.get(".text")) {
//                parseEx(sec.getOffset(), sec.getSize());
//            }
//        }
    }

    public String parseStrTab(int offset, int size) throws IOException {
        if (offset < globalPosition) {
            input = new FileInputStream(inputFileName);
            globalPosition = 0;
        }
        while (globalPosition < offset) {
            input.read();
            globalPosition++;
        }
        StringBuilder strtab = new StringBuilder();
        while (globalPosition < offset + size) {
            strtab.append((char)parseNByteValue(1));

        }
        System.err.println(strtab);
        return strtab.toString();
    }

    public void parseEx(int offset, int size) throws IOException {
        if (offset < globalPosition) {
            input = new FileInputStream(inputFileName);
            globalPosition = 0;
        }
        while (globalPosition < offset) {
            input.read();
            globalPosition++;
        }
//        for (int i = 0; i < size; i++) {
//            System.out.print(input.read() + " ");
//            globalPosition++;
//        }
        while (globalPosition < offset + size) {
            int ex = parse32bit();
            int opcode = ex & 0x0000007f;
            int from11to7 = (ex & 0x00000f80) >> 7;
            switch (opcode) {
                case 0x37 -> System.err.println("LUI " + reg(from11to7));

            }
        }
    }

    public String reg(int code) {
        return switch (code) {
            case 0 -> "zero";
            case 1 -> "ra";
            case 2 -> "sp";
            case 3 -> "gp";
            case 4 -> "tp";
            case 5 -> "t0";
            case 6 -> "t1";
            case 7 -> "t2";
            case 8 -> "s0";
            case 9 -> "s1";
            case 10, 11, 12, 13, 14, 15, 16, 17 -> "a" + (code - 10);
            case 18, 19, 20, 21, 22, 23, 24, 25, 26, 27 -> "s" + (code - 16);
            case 28, 29, 30, 31 -> "t" + (code - 25);
            default -> "";
        };
    }
    public void parseSymTab(int offset, int size) throws IOException {
        if (offset < globalPosition) {
            input = new FileInputStream(inputFileName);
            globalPosition = 0;
        }
        while (globalPosition < offset) {
            input.read();
            globalPosition++;
        }
        System.out.printf("%s %-15s %7s %-8s %-8s %-8s %6s %s\n", "Symbol", "Value", "Size", "Type", "Bind", "Vis", "Index", "Name");
        for (int i = 0; i < size / 16; i++) {
            int st_name = parse32bit();
            int st_value = parse32bit();
            int st_size = parse32bit();
            int st_info = parseNByteValue(1);
            int st_other = parseNByteValue(1);
            int st_shndx = parseNByteValue(2);
            System.out.printf("[%4d] 0x%-15X %5d %-8s %-8s %-8s %6s %s\n", i, st_value, st_size, st_info & 0xf, st_info >> 4, st_other & 0x3, st_shndx, parseStrTable(st_name));

        }
        //"[%4i] 0x%-15X %5i %-8s %-8s %-8s %6s %s\n"
    }


    public int parse32bit() throws IOException {
        return parseNByteValue(4);
    }

    private String parseStrTable(int i) {
        int v = i;
        while (strtab.charAt(v) != 0) {
            v++;
        }
        return strtab.substring(i, v);
    }

    public int parseNByteValue(int n) throws IOException {
        int val = 0;
        for (int i = 0; i < n; i++) {
            int temp = input.read();
            globalPosition++;
            temp <<= 8 * i;
            val += temp;
        }
        return val;
    }
}
