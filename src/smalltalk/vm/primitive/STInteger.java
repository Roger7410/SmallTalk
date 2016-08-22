package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

/** A backing object for smalltalk integers */
public class STInteger extends STObject {
	public final int v;

	public STInteger(VirtualMachine vm, int v) {
		super(vm.lookupClass("Integer"));
		this.v = v;
	}

//    Integer_ADD(STInteger::perform), // +
//    Integer_SUB(STInteger::perform),
//    Integer_MULT(STInteger::perform),
//    Integer_DIV(STInteger::perform),
//    Integer_LT(STInteger::perform),
//    Integer_LE(STInteger::perform),
//    Integer_GT(STInteger::perform),
//    Integer_GE(STInteger::perform),
//    Integer_EQ(STInteger::perform),
//    Integer_MOD(STInteger::perform),
//    Integer_ASFLOAT(STInteger::perform),
	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		STInteger receiver = (STInteger)receiverObj;
		STObject result = vm.nil();
		int v;
		STObject ropnd;
		switch ( primitive ) {
			case Integer_ADD:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				v = receiver.v + ((STInteger)ropnd).v;
				result = new STInteger(vm, v);
				break;
			case Integer_SUB:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				v = receiver.v - ((STInteger)ropnd).v;
				result = new STInteger(vm, v);
				break;
            case Integer_MULT:
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v * ((STInteger)ropnd).v;
                result = new STInteger(vm, v);
                break;
            case Integer_DIV:
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v / ((STInteger)ropnd).v;
                result = new STInteger(vm, v);
                break;
            case Integer_LT: //less than
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v - ((STInteger)ropnd).v;
                result = new STBoolean(vm, v < 0);
                break;
            case Integer_LE: //less or equal
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v - ((STInteger)ropnd).v;
                result = new STBoolean(vm, v <= 0);
                break;
            case Integer_GT:
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v - ((STInteger)ropnd).v;
                result = new STBoolean(vm, v > 0);
                break;
            case Integer_GE:
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v - ((STInteger)ropnd).v;
                result = new STBoolean(vm, v >= 0);
                break;
			case Integer_EQ:
				ropnd = ctx.stack[firstArg]; // get right operand (first arg)
				ctx.sp--; // pop ropnd
				ctx.sp--; // pop receiver
				result = new STBoolean(vm, receiver.v == ((STInteger)ropnd).v);
				break;
            case Integer_MOD:
                ropnd = ctx.stack[firstArg]; // get right operand (first arg)
                ctx.sp--; // pop ropnd
                ctx.sp--; // pop receiver
                v = receiver.v % ((STInteger)ropnd).v;
                result = new STInteger(vm, v);
                break;
            case Integer_ASFLOAT:
                ctx.pop();
                result = new STFloat(vm, (float)(receiver.v));
                break;
            default:
                break;
		}
		return result;
	}

	@Override
	public String toString() {
		return String.valueOf(v);
	}
}
