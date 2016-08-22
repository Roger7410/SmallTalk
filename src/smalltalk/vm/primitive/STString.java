package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

public class STString extends STObject {
	public final String s;

	public STString(VirtualMachine vm, char c) {
		this(vm, String.valueOf(c));
	}

	public STString(VirtualMachine vm, String s) {
		super(vm.lookupClass("String"));
		this.s = s;
	}

//    String_Class_NEW(STString::perform),
//    String_CAT(STString::perform),
//    String_EQ(STString::perform),
//    String_ASARRAY(STString::perform),
	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		vm.assertNumOperands(nArgs+1); // ensure args + receiver
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		STObject result = vm.nil();
        STObject obj;
		switch ( primitive ) {
            case String_Class_NEW:
                obj = ctx.pop();
                ctx.sp--;
                result = new STString(vm,obj.toString());
                break;
            case String_CAT:
                obj = ctx.pop();
                ctx.sp--;
                result = new STString(vm,receiverObj.toString()+obj.toString());
                break;
            case String_EQ:
                obj = ctx.pop();
                ctx.sp--;
                result = new STBoolean(vm,obj.toString().equals(receiverObj.toString()));
                break;
            case String_ASARRAY:
                STObject[] cArray = new STObject[receiverObj.toString().length()];
                for (int i=0;i<receiverObj.toString().length();i++){
                    cArray[i]=new STCharacter(vm,receiverObj.toString().charAt(i));
                }
                result = new STArray(vm,cArray);
                break;
            default:
                break;
		}
		return result;
	}

	public STString asString() { return this; }
	public String toString() { return s; }
}
