package smalltalk.vm.primitive;

import smalltalk.compiler.STField;
import smalltalk.vm.VirtualMachine;

import java.text.DecimalFormat;

/** Backing class for Smalltalk Float. */
public class STFloat extends STObject {
	public final float v;

	public STFloat(VirtualMachine vm, float v) {
		super(vm.lookupClass("Float"));
		this.v = v;
	}

//    Float_ADD(STFloat::perform), // +
//    Float_SUB(STFloat::perform),
//    Float_MULT(STFloat::perform),
//    Float_DIV(STFloat::perform),
//    Float_LT(STFloat::perform),
//    Float_LE(STFloat::perform),
//    Float_GT(STFloat::perform),
//    Float_GE(STFloat::perform),
//    Float_EQ(STFloat::perform),
//    Float_ASINTEGER(STFloat::perform),
	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {

        VirtualMachine vm = ctx.vm;
        int firstArg = ctx.sp - nArgs + 1;
        STObject receiverObj = ctx.stack[firstArg - 1];
        STFloat receiver = (STFloat)receiverObj;
        STObject result = vm.nil();
        float v;
        STObject ropnd;
        switch (primitive){
            case Float_ADD:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v + ((STFloat)ropnd).v;
                result = new STFloat(vm, v);
                break;
            case Float_SUB:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v - ((STFloat)ropnd).v;
                result = new STFloat(vm, v);
                break;
            case Float_MULT:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v * ((STFloat)ropnd).v;
                result = new STFloat(vm, v);
                break;
            case Float_DIV:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v / ((STFloat)ropnd).v;
                result = new STFloat(vm, v);
                break;
            case Float_LT:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v - ((STFloat)ropnd).v;
                result = vm.newBoolean(v<0);
                break;
            case Float_LE:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v - ((STFloat)ropnd).v;
                result = vm.newBoolean(v<=0);
                break;
            case Float_GT:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v - ((STFloat)ropnd).v;
                result = vm.newBoolean(v>0);
                break;
            case Float_GE:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v - ((STFloat)ropnd).v;
                result = vm.newBoolean(v>=0);
                break;
            case Float_EQ:
                ropnd = ctx.pop();
                ctx.sp--;
                v = receiver.v - ((STFloat)ropnd).v;
                result = vm.newBoolean(v==0);
                break;
            case Float_ASINTEGER:
                ctx.pop();
                result = new STInteger(vm,(int)(receiver.v));
                break;
            default:
                break;
        }
        return result;
	}

	@Override
	public String toString() {
		DecimalFormat df = new DecimalFormat("#.#####");
		return df.format(v);
	}
}
