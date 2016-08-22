package smalltalk.vm.primitive;

import org.antlr.symtab.Utils;
import smalltalk.vm.VirtualMachine;

import java.util.Arrays;
import java.util.List;

/** */
public class STArray extends STObject {
	public final STObject[] elements;

	public STArray(VirtualMachine vm, int n, STObject fill) {
		super(vm.lookupClass("Array"));
		elements = new STObject[n];
        for (int i = 0; i < n ; i++){
            elements[i] = fill;
        }
	}

    public STArray(VirtualMachine vm, STObject[] stObjects) {
        super(vm.lookupClass("Array"));
        elements = stObjects;
    }

//    Array_Class_NEW(STArray::perform),
//    Array_SIZE(STArray::perform),
//    Array_AT(STArray::perform),
//    Array_AT_PUT(STArray::perform),
	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
        VirtualMachine vm = ctx.vm;
        int firstArg = ctx.sp - nArgs + 1;
        STObject receiverObj = ctx.stack[firstArg - 1];
        STObject result = vm.nil();
        STInteger obj;
        switch ( primitive ) {
            case Array_Class_NEW:
                obj = (STInteger)ctx.pop();
                ctx.sp--;
                result = new STArray(vm, (obj).v, vm.nil());
                break;
            case Array_SIZE:
                ctx.pop();
                result = new STInteger(vm, ((STArray) receiverObj).elements.length);
                break;
            case Array_AT:
                obj = (STInteger)ctx.pop();
                receiverObj = ctx.pop();
                result = ((STArray) receiverObj).elements[obj.v - 1];
                break;
            case Array_AT_PUT:
                STObject put;
                put = ctx.pop();
                obj = (STInteger)ctx.pop();
                receiverObj = ctx.pop();
                ((STArray) receiverObj).elements[obj.v - 1] = put;
                break;
            default:
                break;
        }
        return result;

	}

	@Override
	public String toString() {
        String s = "";
        for(STObject obj: elements){
            s += obj.toString();
            s += ". ";
        }
        //delete the last ". "
        s = s.substring(0,s.length()-2);
        s = "{"+s+"}";
		return s;
	}
}
