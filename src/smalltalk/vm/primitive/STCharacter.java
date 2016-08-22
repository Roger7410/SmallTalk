package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

public class STCharacter extends STObject {
	public final int c;

	public STCharacter(VirtualMachine vm, int c) {
		super(vm.lookupClass("Character"));
		this.c = c;
	}

//    Character_ASINTEGER(STCharacter::perform),
//    Character_Class_NEW(STCharacter::perform),
	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
        VirtualMachine vm = ctx.vm;
        int firstArg = ctx.sp - nArgs + 1;
        STObject receiverObj = ctx.stack[firstArg - 1];
        //   STCharacter receiver = (STCharacter)receiverObj;
        STObject result = vm.nil();
        STInteger obj;
        switch (primitive){
            case Character_ASINTEGER:
                ctx.sp--;
                //odd
                result = new STInteger( vm, ((STCharacter)receiverObj).c );
                break;
            case Character_Class_NEW:
                obj = (STInteger)ctx.pop();
                ctx.sp--;
                result = new STCharacter( vm, obj.v );
                break;
            default:
                break;
        }
        return result;
	}

	@Override
	public String toString() {
        char r = (char)c;
        return "$" + r;
	}
}
