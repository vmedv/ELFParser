import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

public final class ParseELF {
    private final RandomAccessFile read;
    private final RandomAccessFile write;
    private Elf32_Ehdr header;
    private final List<Elf32_Shdr> sections = new ArrayList<>();
    private final Map<String, Integer> parsedSections = new HashMap<>();
    private String strtab;
    private final List<Elf32_Sym> symtab = new ArrayList<>();
    private final Map<Integer, String> jumps_branches = new HashMap<>();

    public ParseELF(String read, String write) throws IOException {
        this.read = new RandomAccessFile(read, "r");
        this.write = new RandomAccessFile(write, "rw");
        this.write.setLength(0);
    }

    public void parse() throws IOException {
        parseElfHeader();
        parseSectionHeader();
        parse_shstrtab();
        strtab = parse_strtab();
        parse_symtab();
//        findNotMarked();
        parse_text();
        write_symtab();
    }

    private void parseElfHeader() throws IOException {
        header = new Elf32_Ehdr();
        header.e_ident = new char[16];
        read.seek(0);
        for (int i = 0; i <= 9; i++) {
            header.e_ident[i] = (char) read.read();
        }
        read.seek(16);
        if (header.e_ident[0] != 127 || header.e_ident[1] != 'E' || header.e_ident[2] != 'L' || header.e_ident[3] != 'F') {
            throw new IncorrectInputException("Not an ELF File provided");
        }
        if (header.e_ident[4] != 1) {
            throw new IncorrectInputException("Not a 32bit ELF file provided");
        }
        if (header.e_ident[5] != 1) {
            throw new IncorrectInputException("Not a little endian file provided");
        }
        if (header.e_ident[6] == 0) {
            throw new IncorrectInputException("Incorrect version");
        }
        header.e_type = new Elf32_Half(read.read());
        if (0 <= header.e_type.value && header.e_type.value <= 4) {
            read.read();
        } else if (header.e_type.value == 0xff) {
            header.e_type.value <<= 8;
            header.e_type.value += read.read();
        } else {
            throw new IncorrectInputException("Incorrect file type");
        }
        header.e_machine = new Elf32_Half(read.read());
        if (header.e_machine.value != 0xf3) {
            throw new IncorrectInputException("Not a RISC_V file provided");
        }
        read.read();
        header.e_version = new Elf32_Word(read32bit());
        if (header.e_version.value == 0) {
            throw new IncorrectInputException("Incorrect version");
        }
        header.e_entry = new Elf32_Addr(read32bit());
        header.e_phoff = new Elf32_Off(read32bit());
        header.e_shoff = new Elf32_Off(read32bit());
        header.e_flags = new Elf32_Word(read32bit());
        header.e_ehsize = new Elf32_Half( read16bit());
        header.e_phentsize = new Elf32_Half( read16bit());
        header.e_phnum = new Elf32_Half(read16bit());
        header.e_shentsize = new Elf32_Half(read16bit());
        header.e_shnum = new Elf32_Half(read16bit());
        header.e_shstrndx = new Elf32_Half(read16bit());
    }

    private void parseSectionHeader() throws IOException {
        read.seek(header.e_shoff.value);
        for (int i = 0; i < header.e_shnum.value; i++) {
            Elf32_Shdr section = new Elf32_Shdr();
            section.sh_name = new Elf32_Word(read32bit());
            section.sh_type = new Elf32_Word(read32bit());
            section.sh_flags = new Elf32_Word(read32bit());
            section.sh_addr = new Elf32_Addr(read32bit());
            section.sh_offset = new Elf32_Off(read32bit());
            section.sh_size = new Elf32_Word(read32bit());
            section.sh_link = new Elf32_Word(read32bit());
            section.sh_info = new Elf32_Word(read32bit());
            section.sh_addralign = new Elf32_Word(read32bit());
            section.sh_entsize = new Elf32_Word(read32bit());

            sections.add(section);
        }
    }

    private void parse_shstrtab() throws IOException {
        read.seek(sections.get(header.e_shstrndx.value).sh_offset.value);
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < sections.get(header.e_shstrndx.value).sh_size.value; i++) {
            name.append((char) read.read());
            if (".symtab".equals(name.toString())) {
                for (int sec = 0; sec < sections.size(); sec++) {
                    if (sections.get(sec).sh_name.value == i - name.length() + 1) {
                        parsedSections.put(".symtab", sec);
                        break;
                    }
                }
                name = new StringBuilder();
            } else if (".text".equals(name.toString())) {
                for (int sec = 0; sec < sections.size(); sec++) {
                    if (sections.get(sec).sh_name.value == i - name.length() + 1) {
                        parsedSections.put(".text", sec);
                        break;
                    }
                }
                name = new StringBuilder();
            } else if (".strtab".equals(name.toString())) {
                for (int sec = 0; sec < sections.size(); sec++) {
                    if (sections.get(sec).sh_name.value == i - name.length() + 1) {
                        parsedSections.put(".strtab", sec);
                        break;
                    }
                }
                name = new StringBuilder();
            } else if (!".symtab".startsWith(name.toString()) &&
                       !".text".startsWith(name.toString()) &&
                       !".strtab".startsWith(name.toString())) {
                name = new StringBuilder();
            }
        }
    }

    private String parse_strtab() throws IOException {
        read.seek(sections.get(parsedSections.get(".strtab")).sh_offset.value);
        StringBuilder build_strtab = new StringBuilder();
        for (int i = 0; i < sections.get(parsedSections.get(".strtab")).sh_size.value; i++) {
            build_strtab.append((char) read.read());
        }
        return build_strtab.toString();
    }

    private void parse_symtab() throws IOException {
        read.seek(sections.get(parsedSections.get(".symtab")).sh_offset.value);
        for (int i = 0; i < sections.get(parsedSections.get(".symtab")).sh_size.value / 16; i++) {
            Elf32_Sym sym = new Elf32_Sym();
            sym.st_name = new Elf32_Word(read32bit());
            sym.st_value = new Elf32_Addr(read32bit());
            sym.st_size = new Elf32_Word(read32bit());
            sym.st_info = (byte) read.read();
            sym.st_other = (byte) read.read();
            sym.st_shndx = new Elf32_Half(read16bit());
            symtab.add(sym);
        }
        for (int i = 0; i < symtab.size(); i++) {
            if ((symtab.get(i).st_info & 0xf) == 2) {
                jumps_branches.put(symtab.get(i).st_value.value,
                        getName(symtab.get(i).st_name.value).equals("") ?
                                String.format(
                                        "LOC_%05x",
                                        symtab.get(i).st_value.value)
                        : getName(symtab.get(i).st_name.value));
            }
        }
    }

    private void parse_text() throws IOException {
        write.writeBytes(".text\n");
        read.seek(sections.get(parsedSections.get(".text")).sh_offset.value);
        for (int pc = 0; pc < sections.get(parsedSections.get(".text")).sh_size.value;) {
            Command temp = readCommand();
            Command32bit temp32;
            Command16bit temp16;
            if (temp instanceof Command32bit) {
                temp32 = (Command32bit) temp;
                write.writeBytes(temp32.load_store ?
                        String.format(
                                (jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, "").equals("") ?
                                        "%08x %10s  %s %s, %s(%s)\n" :
                                        "%08x %10s: %s %s, %s(%s)\n"),
                                sections.get(parsedSections.get(".text")).sh_addr.value + pc,
                                jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, ""),
                                temp32.getCommand(),
                                switch (temp32.getCommand()) {
                                    case
                                            "lb", "lw", "lh",
                                            "lbu", "lhu" -> getRegister(temp32.from11to7);
                                    case "sb", "sh", "sw" -> getRegister(temp32.from24to20);
                                    default -> "";
                                },
                                switch (temp32.getCommand()) {
                                    case
                                            "lb", "lh", "lw",
                                            "lbu", "lhu" -> temp32.buildITypeImm();
                                    case "sb", "sh", "sw" -> temp32.buildSTypeImm();
                                    default -> "";
                                },
                                getRegister(temp32.from19to15)
                        ) :
                        String.format(
                                (jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, "").equals("") ?
                                        (temp32.type == Command32bit.Optype.E ||
                                                temp32.getCommand().equals("jal") ||
                                                temp32.getCommand().equals("lui") ||
                                                temp32.getCommand().equals("auipc") ? "%08x %10s  %s %s, %s %s\n" :
                                                "%08x %10s  %s %s, %s, %s\n") :
                                        (temp32.type == Command32bit.Optype.E ||
                                                temp32.getCommand().equals("jal") ||
                                                temp32.getCommand().equals("lui") ||
                                                temp32.getCommand().equals("auipc") ? "%08x %10s: %s %s, %s %s\n" :
                                                "%08x %10s: %s %s, %s, %s\n")),
                                sections.get(parsedSections.get(".text")).sh_addr.value + pc,
                                jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, ""),
                                temp32.getCommand(),
                                switch (temp32.getCommand()) {
                                    case
                                            "lui", "auipc", "addi",
                                            "slti", "stliu", "xori",
                                            "ori", "andi", "slli",
                                            "srli", "srai", "add",
                                            "sub", "sll", "slt",
                                            "sltu", "xor", "srl",
                                            "sra", "or", "and",
                                            "jal", "jalr", "mul",
                                            "mulh", "mulhsu", "mulhu",
                                            "div", "divu", "rem",
                                            "remu" -> getRegister(temp32.from11to7);
                                    case
                                            "beq", "bne", "blt",
                                                    "bge", "bltu", "bgeu" -> getRegister(temp32.from19to15);
                                    default -> "";
                                },
                                switch (temp32.getCommand()) {
                                    case
                                            "lui", "auipc" -> temp32.buildUTypeImm();
                                    case
                                            "addi", "slti", "sltiu",
                                            "xori", "ori", "andi",
                                            "slli", "srli", "srai",
                                            "add", "sub", "sll",
                                            "slt", "sltu", "xor",
                                            "srl", "sra", "or",
                                            "and", "jalr", "mul",
                                            "mulh", "mulhsu", "mulhu",
                                            "div", "divu", "rem",
                                            "remu"  -> getRegister(temp32.from19to15);
                                    case "jal" -> temp32.buildJTypeImm() + sections.get(parsedSections.get(".text")).sh_addr.value + pc;
                                    case
                                            "beq", "bne", "blt",
                                            "bge", "bltu","bgeu" -> getRegister(temp32.from24to20);
                                    default -> "";
                                },
                                switch (temp32.getCommand()) {
                                    case
                                            "addi", "slti", "sltiu",
                                            "xori", "ori", "andi",
                                            "jalr" -> temp32.buildITypeImm();
                                    case "slli", "srli", "srai" -> temp32.from24to20;
                                    case
                                            "add", "sub", "sll",
                                            "slt", "sltu", "xor",
                                            "srl", "sra", "or",
                                            "and", "mul", "mulh",
                                            "mulhsu", "mulhu", "div",
                                            "divu", "rem", "remu"  -> getRegister(temp32.from24to20);
                                    case
                                            "beq", "bne", "blt",
                                            "bge", "bltu","bgeu" -> temp32.buildBTypeImm() +
                                            sections.get(parsedSections.get(".text")).sh_addr.value +
                                            pc;
                                    default -> "";
                                }
                        ));
                pc += 4;
            } else {
                temp16 = (Command16bit) temp;
                write.writeBytes(temp16.load_store ?
                        String.format(
                                (jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, "").equals("") ?
                                        "%08x %10s  %s %s, %s(%s)\n" :
                                        "%08x %10s: %s %s, %s(%s)\n"),
                                sections.get(parsedSections.get(".text")).sh_addr.value + pc,
                                jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, ""),
                                temp16.getCommand(),
                                switch (temp16.getCommand()) {
                                    case "c.lw", "c.sw" -> getRVCRegister(temp16.from4to2);
                                    case "c.swsp" -> getRegister((temp16.from6to5 << 3) + temp16.from4to2);
                                    case "c.lwsp" -> getRegister(((temp16.from12to10 & 0b011) << 3) + temp16.from9to7);
                                    default -> "";
                                },
                                switch (temp16.getCommand()) {
                                    case "c.lw" -> ((temp16.from6to5 & 0b01) << 6) +
                                            (temp16.from12to10 << 3) +
                                            ((temp16.from6to5 & 0b10) << 2);
                                    case "c.sd" -> (temp16.from6to5 << 6) + (temp16.from12to10 << 3);
                                    case "c.lwsp" -> ((temp16.from4to2 & 0b011) << 6) +
                                            ((temp16.from12to10 & 0b100) << 3) +
                                            (temp16.from6to5 << 3) +
                                            (temp16.from4to2 & 0b100);
                                    case "c.swsp" -> ((temp16.from9to7 & 0b011) << 6) + (temp16.from12to10 << 3) + (temp16.from9to7 & 0b001 << 2);
                                    default -> "";
                                },
                                switch (temp16.getCommand()) {
                                    case "c.lw", "c.sw" -> getRVCRegister(temp16.from9to7);
                                    case "c.swsp", "c.lwsp" -> "sp";
                                    default -> "";
                                }

                        ) :
                        String.format(
                                (jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, "").equals("") ?
                                        "%08x %10s  %s\n" : "%08x %10s: %s \n"),
                                sections.get(parsedSections.get(".text")).sh_addr.value + pc,
                                jumps_branches.getOrDefault(sections.get(parsedSections.get(".text")).sh_addr.value + pc, ""),
                                switch (temp16.getCommand()) {
                                    case "c.addi4spn" -> "c.addi4spn " + getRVCRegister(temp16.from4to2) + ", sp, " + temp16.c_12_5_value();
                                    case "c.addi" -> "c.addi " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7) + ", " +
                                            temp16.c_addi_value();
                                    case "c.jal", "c.j" -> temp16.getCommand() + " " + (temp16.c_jal_value() + sections.get(parsedSections.get(".text")).sh_addr.value + pc);
                                    case "c.li" -> "c.li " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7) + ", " + temp16.c_addi_value();
                                    case "c.addi16sp" -> "c.addi16sp sp, " + temp16.c_addi16sp_value();
                                    case "c.lui" -> "c.lui " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7) + ", " + temp16.c_lui_value();
                                    case "c.srli", "c.srai", "c.andi" -> temp16.getCommand() + " " + getRVCRegister(temp16.from9to7) + ", " + temp16.c_shift_value();
                                    case "c.sub", "c.xor", "c.or", "c.and" -> temp16.getCommand() + " " + getRVCRegister(temp16.from9to7) + ", " + getRVCRegister(temp16.from4to2);
                                    case "c.slli" -> "c.slli " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7) + ", " + temp16.c_shift_value();
                                    case "c.beqz", "c.bnez" -> temp16.getCommand() + " " + getRVCRegister(temp16.from9to7) +
                                            ", " + (temp16.c_branch_value() + sections.get(parsedSections.get(".text")).sh_addr.value + pc);
                                    case "c.jr" -> "c.jr " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7);
                                    case "c.mv", "c.add" -> temp16.getCommand() + " " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7) + ", " + getRegister((temp16.from6to5 << 3) + temp16.from4to2);
                                    case "c.ebreak" -> "c.ebreak";
                                    case "c.jalr" -> "c.jalr " + getRegister(((temp16.from12to10 & 0b011) << 3) +
                                            temp16.from9to7);
                                    default -> "";
                                }
                        ));
                pc += 2;
            }

        }
    }

    private void write_symtab() throws IOException {
        write.writeBytes("\n.symtab\n");
        write.writeBytes(String.format("%s %-15s %7s %-8s %-8s %-8s %6s %s\n",
                "Symbol", "Value", "Size", "Type", "Bind", "Vis", "Index", "Name"));
        for (int i = 0; i < symtab.size(); i++) {
            write.writeBytes(
                    String.format(
                        "[%4d] 0x%-15X %5d %-8s %-8s %-8s %6s %s\n",
                        i,
                        symtab.get(i).st_value.value,
                        symtab.get(i).st_size.value,
                        getType(symtab.get(i).st_info & 0xf),
                        getBind(symtab.get(i).st_info >> 4),
                        getVisibility(symtab.get(i).st_other & 0x3),
                        getIndex(symtab.get(i).st_shndx.value),
                        getName(symtab.get(i).st_name.value)
                    )
            );
        }
    }

    private String getType(int v) {
        return switch (v) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 14 -> "SPARC_REGISTER";
            case 15 -> "HIPROC";
            default -> "";
        };
    }

    private String getBind(int v) {
        return switch (v) {
            case 0 -> "L0CAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            case 10 -> "LOOS";
            case 12 -> "HIOS";
            case 13 -> "LOPROC";
            case 15 -> "HIPROC";
            default -> "";
        };
    }

    private String getVisibility(int v) {
        return switch (v) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            case 4 -> "EXPORTED";
            case 5 -> "SINGLETON";
            case 6 -> "ELIMINATE";
            default -> "";
        };
    }

    private String getIndex(int v) {
        return switch (v) {
            case 0 -> "UNDEF";
            case 0xff00 -> "LOPROC";
            case 0xff01 -> "AFTER";
            case 0xff02 -> "AMD64_LCOMMON";
            case 0xff1f -> "HIPROC";
            case 0xff20 -> "LOOS";
            case 0xff3f -> "HIOS";
            case 0xfff1 -> "ABS";
            case 0xfff2 -> "COMMON";
            case 0xffff -> "XINDEX";
            default -> String.valueOf(v);
        };
    }

    public String getRegister(int code) {
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

    public String getRVCRegister(int code) {
        return switch (code) {
            case 0, 1 -> "s" + code;
            default -> "a" + (code - 2);
        };
    }

    private String getName(int name) {
        int endPointer = name;
        while (strtab.charAt(endPointer) != '\0') {
            endPointer++;
        }
        return strtab.substring(name, endPointer);
    }

    private int read32bit() throws IOException {
        return read8Nbit(4);
    }

    private int read16bit() throws IOException {
        return read8Nbit(2);
    }

    private int read8Nbit(int n) throws IOException {
        int val = 0;
        for (int i = 0; i < n; i++) {
            int temp = read.read();
            temp <<= 8 * i;
            val += temp;
        }
        return val;
    }

    private Command readCommand() throws IOException {
        int temp = read32bit();
        Command16bit command16 = new Command16bit();
        Command32bit command32 = new Command32bit();
        if ((temp & 0b11) == 0b11) {
            command32.from6to0 = temp & 0x0000007f;
            command32.from11to7 = (temp >> 7) & 0x0000001f;
            command32.from14to12 = (temp >> 12) & 0x00000007;
            command32.from19to15 = (temp >> 15) & 0x0000001f;
            command32.from24to20 = (temp >> 20) & 0x0000001f;
            command32.from31to25 = (temp >> 25) & 0x0000007f;
            command32.setCode();
            return command32;
        } else {
            read.seek(read.getFilePointer() - 2);
            command16.from1to0 = temp & 0x0003;
            command16.from4to2 = (temp >> 2) & 0x0007;
            command16.from6to5 = (temp >> 5) & 0x0003;
            command16.from9to7 = (temp >> 7) & 0x0007;
            command16.from12to10 = (temp >> 10) & 0x0007;
            command16.from15to13 = (temp >> 13) & 0x0007;
            command16.setCode();
            return command16;
        }
    }

    public void close() throws IOException {
        read.close();
        write.close();
    }
}
