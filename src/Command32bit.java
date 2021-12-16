public class Command32bit implements Command {
    enum Optype {
        R, I, S, B, U, J, E
    }
    int from31to25;
    int from24to20;
    int from19to15;
    int from14to12;
    int from11to7;
    int from6to0;
    Optype type;
    boolean load_store = false;

    public int buildJTypeImm() {
        int val = (((this.from31to25 & 0x40) << 14) +
                (this.from19to15 << 15) +
                (this.from14to12 << 12) +
                ((this.from24to20 & 0x1) << 11) +
                ((this.from31to25 & 0x3f) << 5) +
                (this.from24to20 & 0x1e));
        return (val & 0x100000) == 0 ? val & 0xfffff : -((val ^ 0x1fffff) + 1);
    }

    public int buildITypeImm() {
        int val = (this.from31to25 << 5) +
                (this.from24to20);
        return (val & 0x800) == 0 ? val & 0x7ff : -((val ^ 0xfff) + 1);
    }

    public int buildSTypeImm() {
        int val = (this.from31to25 << 5) + (this.from11to7);
        return (val & 0x800) == 0 ? val & 0x7ff : -((val ^ 0xfff) + 1);
    }

    public int buildBTypeImm() {
        int val = ((this.from31to25 & 0x40) << 6) +
                ((this.from11to7 & 0x1) << 11) +
                ((this.from31to25 & 0x3f) << 5) +
                (this.from11to7 & 0x1e);
        return (val & 0x1000) == 0 ? val & 0xfff : -((val ^ 0x1fff) + 1);
    }

    public int buildUTypeImm() {
        return (this.from31to25 << 25) +
                (this.from24to20 << 20) +
                (this.from19to15 << 15) +
                (this.from14to12 << 12);
    }

    public void setCode() {
        switch (this.from6to0) {
            case 0b0110111, 0b0010111 -> this.type = Optype.U;
            case 0b1101111 -> this.type = Optype.J;
            case 0b1100111, 0b0000011, 0b0010011 -> this.type = Optype.I;
            case 0b1100011 -> this.type = Optype.B;
            case 0b0100011 -> this.type = Optype.S;
            case 0b0110011 -> this.type = Optype.R;
            case 0b1110011 -> this.type = Optype.E;
        }
        if (this.from6to0 == 0b0000011 || this.from6to0 == 0b0100011) {
            load_store = true;
        }
    }

    public String getCommand() {
        return switch (this.from6to0) {
            case 0b0110111 -> "lui";
            case 0b0010111 -> "auipc";
            case 0b1101111 -> "jal";
            case 0b1100111 -> "jalr";
            case 0b1100011 -> switch (this.from14to12) {
                case 0b000 -> "beq";
                case 0b001 -> "bne";
                case 0b100 -> "blt";
                case 0b101 -> "bge";
                case 0b110 -> "bltu";
                case 0b111 -> "bgeu";
                default -> "unknown_command";
            };
            case 0b0000011 -> switch (this.from14to12) {
                case 0b000 -> "lb";
                case 0b001 -> "lh";
                case 0b010 -> "lw";
                case 0b100 -> "lbu";
                case 0b101 -> "lhu";
                default -> "unknown_command";
            };
            case 0b0100011 -> switch (this.from14to12) {
                case 0b000 -> "sb";
                case 0b001 -> "sh";
                case 0b010 -> "sw";
                default -> "unknown_command";
            };
            case 0b0010011 -> switch (this.from14to12) {
                case 0b000 -> "addi";
                case 0b010 -> "slti";
                case 0b011 -> "sltiu";
                case 0b100 -> "xori";
                case 0b110 -> "ori";
                case 0b111 -> "andi";
                case 0b001 -> "slli";
                case 0b101 -> switch (this.from31to25) {
                    case 0b0000000 -> "srli";
                    case 0b0100000 -> "srai";
                    default -> "unknown_command";
                };
                default -> "unknown_command";
            };
            case 0b0110011 -> switch (this.from14to12) {
                case 0b000 -> switch (this.from31to25) {
                    case 0b0000000 -> "add";
                    case 0b0100000 -> "sub";
                    case 0b0000001 -> "mul";
                    default -> "unknown_command";
                };
                case 0b001 -> switch (this.from31to25) {
                    case 0b0000000 -> "sll";
                    case 0b0000001 -> "mulh";
                    default -> "unknown_command";
                };
                case 0b010 -> switch (this.from31to25) {
                    case 0b0000000 -> "slt";
                    case 0b0000001 -> "mulhsu";
                    default -> "unknown_command";
                };
                case 0b011 -> switch (this.from31to25) {
                    case 0b0000000 -> "sltu";
                    case 0b0000001 -> "mulhu";
                    default -> "unknown_command";
                };
                case 0b100 -> switch (this.from31to25) {
                    case 0b0000000 -> "xor";
                    case 0b0000001 -> "div";
                    default -> "unknown_command";
                };
                case 0b101 -> switch (this.from31to25) {
                    case 0b0000000 -> "srl";
                    case 0b0100000 -> "sra";
                    case 0b0000001 -> "divu";
                    default -> "unknown_command";
                };
                case 0b110 -> switch (this.from31to25) {
                    case 0b0000000 -> "or";
                    case 0b0000001 -> "rem";
                    default -> "unknown_command";
                };
                case 0b111 -> switch (this.from31to25) {
                    case 0b0000000 -> "and";
                    case 0b0000001 -> "remu";
                    default -> "unknown_command";
                };
                default -> "unknown_command";
            };
            case 0b1110011 -> (
                    (this.from31to25 == 0b0000000) &&
                            (this.from19to15 == 0b00000) &&
                            (this.from14to12 == 0b000) &&
                            (this.from11to7 == 0b00000)
            ) ? switch (this.from24to20) {
                case 0b00000 -> "ecall";
                case 0b00001 -> "ebreak";
                default -> "unknown_command";
            } : "unknown_command";
            default -> "unknown_command";
        };
    }
}
