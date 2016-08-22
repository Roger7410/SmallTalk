package smalltalk.vm.primitive;

import smalltalk.vm.VirtualMachine;

/** */
public class STBoolean extends STObject {
	public final boolean b;

	public STBoolean(VirtualMachine vm, boolean b) {
		super(vm.lookupClass("Boolean"));
		this.b = b;
	}

//    Boolean_IFTRUE_IFFALSE(STBoolean::perform),
//    Boolean_IFTRUE(STBoolean::perform),
//    Boolean_NOT(STBoolean::perform),
	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
        VirtualMachine vm = ctx.vm;
        int firstArg = ctx.sp - nArgs + 1;
        STObject receiverObj = ctx.stack[firstArg - 1];
        STBoolean receiver = (STBoolean)receiverObj;
        STObject result = null;
        STObject obj;
        switch (primitive){
            case Boolean_IFTRUE:
                obj = ctx.stack[firstArg];
                ctx.sp-=2;
                if(receiver.b){
                    BlockDescriptor bd = (BlockDescriptor) obj;
                    BlockContext b = new BlockContext(vm,bd);
                    vm.pushContext(b);
                }else
                    result = vm.nil();
                break;
            case Boolean_IFTRUE_IFFALSE:
                obj = ctx.stack[firstArg];
                STObject obj2 = ctx.stack[firstArg+1];
                ctx.sp-=3;
                if(receiver.b){
                    vm.pushContext(new BlockContext(vm,(BlockDescriptor)obj));
                }else{
                    vm.pushContext(new BlockContext(vm,(BlockDescriptor)obj2));
                }
                result = vm.nil();
                break;
            case Boolean_NOT:
                ctx.sp--;
                result = vm.newBoolean(!receiver.b);
                break;
            default:
                break;
        }
        return result;
	}

	@Override
	public String toString() {
		return String.valueOf(b);
	}
}
