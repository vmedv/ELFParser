public class Command16bit implements Command {
    int from15to13;
    int from12to10;
    int from9to7;
    int from6to5;
    int from4to2;
    int from1to0;
    boolean load_store = false;

    public void setCode() {
        if (from1to0 == 0b00 && (from15to13 == 0b010 || from15to13 == 0b110) ||
            from1to0 == 0b10 && (from15to13 == 0b010 || from15to13 == 0b110)) {
            load_store = true;
        }
    }

    public int c_addi_value() {
        int val = ((this.from12to10 & 0b100) << 3) + (this.from6to5 << 3) + this.from4to2;
        return (val & 0x20) == 0 ? val & 0x1f : -((val ^ 0x3f) + 1);
    }

    public int c_12_5_value() {
        int val = ((this.from12to10 & 0b001) << 9) +
                ((this.from9to7) << 6) +
                ((this.from12to10 & 0b110) << 3) +
                ((this.from6to5 & 0b01) << 3) +
                ((this.from6to5 & 0b10) << 1);
        return val;
    }

    public int c_addi16sp_value() {
        int val = ((this.from12to10 & 0b100) << 7) +
                ((this.from4to2 & 0b110) << 6) +
                ((this.from6to5 & 0b01) << 6) +
                ((this.from4to2 & 0b001) << 5) +
                ((this.from6to5 & 0b10) << 3);
        return (val & 0x200) == 0 ? val & 0x1ff : -((val ^ 0x3ff) + 1);
    }

    public int c_lui_value() {
        int val = ((this.from12to10 & 0b100) << 15) + (this.from6to5 << 15) + (this.from4to2 << 12);
        return val;
    }

    public int c_shift_value() {
        int val = ((this.from12to10 & 0b100) << 3) + (this.from6to5 << 4) + this.from4to2;
        return val;
    }

    public int c_branch_value() {
        int val = ((this.from12to10 & 0b100) << 6) +
                (this.from6to5 << 6) +
                ((this.from4to2 & 0b001) << 5) +
                ((this.from12to10 & 0b011) << 3) +
                ((this.from4to2 & 0b110));
        return (val & 0x100) == 0 ? val & 0xff : -((val ^ 0x1ff) + 1);
    }

    public int c_jal_value() {
        int val = ((this.from12to10 & 0b100) << 9) +
                ((this.from9to7 & 0b010) << 9) +
                ((this.from12to10 & 0b001) << 9) +
                ((this.from9to7 & 0b100) << 6) +
                ((this.from6to5 & 0b10) << 6) +
                ((this.from9to7 & 0b001) << 6) +
                ((this.from4to2 & 0b001) << 5) +
                ((this.from12to10 & 0b010) << 3) +
                ((this.from6to5 & 0b01) << 3) +
                ((this.from4to2 & 0b110));
        return (val & 0x800) == 0 ? val & 0x7ff : -((val ^ 0xfff) + 1);
    }

    public String getCommand() {
        return switch (this.from1to0) {
            case 0b00 -> switch (this.from15to13) {
                case 0b000 -> "c.addi4spn";
                case 0b010 -> "c.lw";
                case 0b110 -> "c.sw";
                default -> "unknown_command";
            };
            case 0b01 -> switch (this.from15to13) {
                case 0b000 -> "c.addi";
                case 0b001 -> "c.jal";
                case 0b010 -> "c.li";
                case 0b011 -> ((from12to10 & 0b011) << 3 + from9to7) == 0b00010 ? "c.addi16sp" : "c.lui";
                case 0b100 -> switch (from12to10 & 0b011) {
                    case 0b00 -> "c.srli";
                    case 0b01 -> "c.srai";
                    case 0b10 -> "c.andi";
                    case 0b11 -> switch (this.from12to10 & 0b100) {
                        case 0b000 -> switch (this.from6to5) {
                            case 0b00 -> "c.sub";
                            case 0b01 -> "c.xor";
                            case 0b10 -> "c.or";
                            case 0b11 -> "c.and";
                            default -> "unknown_command";
                        };
                        case 0b100 -> switch (this.from6to5) {
                            default -> "unknown_command";
                        };
                        default -> "unknown_command";
                    };
                    default -> "unknown_command";
                };
                case 0b101 -> "c.j";
                case 0b110 -> "c.beqz";
                case 0b111 -> "c.bnez";
                default -> "unknown_command";
            };
            case 0b10 -> switch (this.from15to13) {
                case 0b000 -> "c.slli";
                case 0b010 -> "c.lwsp";
                case 0b100 -> switch (this.from12to10 & 0b100) {
                    case 0b000 -> ((from6to5 << 4) + from4to2) == 0 ? "c.jr" : "c.mv";
                    case 0b100 -> ((from6to5 << 4) + from4to2) == 0 ? ((from12to10 & 0b011) << 3) + from9to7 == 0 ? "c.ebreak" : "c.jalr" : "c.add";
                    default -> "unknown_command";
                };
                case 0b110 -> "c.swsp";
                default -> "unknown_command";
            };
            default -> "unknown_command";
        };
    }
}
