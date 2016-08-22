package smalltalk.vm;

import org.antlr.symtab.ClassSymbol;
import org.antlr.symtab.Symbol;
import org.antlr.symtab.Utils;
import smalltalk.compiler.STClass;
import smalltalk.compiler.STSymbolTable;
import smalltalk.vm.exceptions.BlockCannotReturn;
import smalltalk.vm.exceptions.ClassMessageSentToInstance;
import smalltalk.vm.exceptions.IndexOutOfRange;
import smalltalk.vm.exceptions.InternalVMException;
import smalltalk.vm.exceptions.MessageNotUnderstood;
import smalltalk.vm.exceptions.MismatchedBlockArg;
import smalltalk.vm.exceptions.StackUnderflow;
import smalltalk.vm.exceptions.TypeError;
import smalltalk.vm.exceptions.UndefinedGlobal;
import smalltalk.vm.exceptions.UnknownClass;
import smalltalk.vm.exceptions.UnknownField;
import smalltalk.vm.exceptions.VMException;
import smalltalk.vm.primitive.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A VM for a subset of Smalltalk.
 *
 *  3 HUGE simplicity factors in this implementation: we ignore GC,
 *  efficiency, and don't expose execution contexts to smalltalk programmers.
 *
 *  Because of the shared {@link SystemDictionary#objects} list (ThreadLocal)
 *  in SystemDictionary, each VirtualMachine must run in its own thread
 *  if you want multiple.
 */
public class VirtualMachine {
	/** The dictionary of global objects including class meta objects */
	public final SystemDictionary systemDict; // singleton
    //done
	/** "This is the active context itself. It is either a BlockContext
	 *  or a BlockContext." BlueBook p 605 in pdf.
	 */
	public BlockContext ctx;

	/** Trace instructions and show stack during exec? */
	public boolean trace = false;

	public VirtualMachine(STSymbolTable symtab) {
		systemDict = new SystemDictionary(this);
		for (Symbol s : symtab.GLOBALS.getSymbols()) {
			if ( s instanceof ClassSymbol ) {
				systemDict.define(s.getName(),
								  new STMetaClassObject(this,(STClass)s));
			}
		}
		STObject transcript = new STObject(systemDict.lookupClass("TranscriptStream"));
		systemDict.define("Transcript", transcript);

        //init
        systemDict.initPredefinedObjects();

		// create system dictionary and predefined Transcript
		// convert symbol table ClassSymbols to STMetaClassObjects
	}

	/** look up MainClass>>main and execute it */
	public STObject execMain() {
        STMetaClassObject mainSTMCO = systemDict.lookupClass("MainClass");
        if (mainSTMCO == null){
            return nil();
        }else{
            STObject mainObject = new STObject(mainSTMCO);
            STCompiledBlock main = mainSTMCO.resolveMethod("main");
            return exec(mainObject, main);
        }
		// ...
//		return exec(mainObject,main);
	}

	/** Begin execution of the bytecodes in method relative to a receiver
	 *  (self) and within a particular VM. exec() creates an initial
	 *  method context to simulate a call to the method passed in.
	 *
	 *  Return the value left on the stack after invoking the method,
	 *  or return self/receiver if there's nothing on the stack.
	 */
	public STObject exec(STObject self, STCompiledBlock method) {
		ctx = null;
		BlockContext initialContext = new BlockContext(this, method, self);
		pushContext(initialContext);
		// fetch-decode-execute loop
		while ( ctx.ip < ctx.compiledBlock.bytecode.length ) {
            int index;
			if ( trace ) traceInstr(); // show instr first then stack after to show results
            ctx.prev_ip = ctx.ip;//fix
            int op = ctx.compiledBlock.bytecode[ctx.ip++];
			switch ( op ) {
				case Bytecode.NIL:
					ctx.push(nil());
					break;
                case Bytecode.SELF:
                    ctx.push(ctx.receiver);
                    break;
                case Bytecode.TRUE:
                    ctx.push(newBoolean(true));
                    //System.out.println(newBoolean(true));
                    break;
                case Bytecode.FALSE:
                    ctx.push(newBoolean(false));
                    break;
                case Bytecode.PUSH_CHAR:
                    char c = (char) consumeShort();
                    ctx.push(newChar(c));
                    break;
                case Bytecode.PUSH_INT:
                    ctx.push(newInteger(consumeInt()));
                    break;
                case Bytecode.PUSH_FLOAT:
                    ctx.push(newFloat(consumeFloat()));
                    break;
                case Bytecode.PUSH_FIELD:
                    index = consumeShort();
//                    if (ctx.receiver.fields[index] == null){
//                        ctx.push(nil());
//                    } else{
                        ctx.push(ctx.receiver.fields[index]);
//                    }
                    break;
                case Bytecode.PUSH_LOCAL:
                    int d = consumeShort();
                    int i = consumeShort();
                    BlockContext blk = ctx;
                    for (int j=0; j<d; j++) {
                        blk = blk.enclosingContext;
                    }
                    ctx.push(blk.locals[i]);
                    break;
                case Bytecode.PUSH_LITERAL:
                    index = consumeShort();
                    ctx.push(newString(ctx.compiledBlock.literals[index]));
                    break;
                case Bytecode.PUSH_GLOBAL:
                    index = consumeShort();
                    ctx.push(systemDict.lookup(ctx.compiledBlock.literals[index]));
                    break;
                case Bytecode.PUSH_ARRAY:
                    ctx.push(newArray(this));
                    break;
                case Bytecode.STORE_FIELD:
                    index = consumeShort();
                    ctx.receiver.fields[index] = ctx.top();
                    break;
                case Bytecode.STORE_LOCAL:
                    d = consumeShort();
                    i = consumeShort();
                    blk = ctx;
                    for (int j=0; j<d; j++) {
                        blk = blk.enclosingContext;
                    }
                    // ctx.stack[ctx.sp]
                    blk.locals[i] = ctx.top();
                    break;
                case Bytecode.POP:
                    ctx.pop();
                    break;
                case Bytecode.SEND: // SEND nargs, literals_index
                    // fetch the nargs, index into the literals table
                    int nargs = consumeShort();
                    STObject receiver = ctx.stack[ctx.sp - nargs];
                    int litindex = consumeShort();

                    String messageName = ctx.compiledBlock.literals[litindex];
                    STCompiledBlock stb;
                    stb = receiver.getSTClass().resolveMethod(messageName);
//                    "ClassMessageSentToInstance: new is a class method sent to instance of Integer\n" +
//                            "    at                              MainClass>>main[][99](<string>:1:3)       executing 0012:  send           0, 'new'\n";
                    if (stb.isClassMethod && !(receiver instanceof STMetaClassObject)) {
                        error("ClassMessageSentToInstance", messageName + " is a class method sent to instance of " + receiver.getSTClass().getName());
                    }else if (!stb.isClassMethod && receiver instanceof STMetaClassObject) {
                        error("MessageNotUnderstood", messageName + " is an instance method sent to class object " + receiver.getSTClass().getName());
                    }

                    if (stb.isPrimitive()){
                        STObject result = stb.primitive.perform(ctx,nargs);
                        if (result != null){
                            ctx.push(result);
                        }
                    }else{
                        BlockContext curCtx = new BlockContext(this, stb, receiver);
                        for(int j=nargs-1; j>=0; j--){
                            curCtx.locals[j] = ctx.pop();
                        }
                        ctx.pop();
                        pushContext(curCtx);
                    }
                    break;
                case Bytecode.SEND_SUPER:
                    // fetch the nargs, index into the literals table
                    nargs = consumeShort();
                    receiver = ctx.stack[ctx.sp - nargs];
                    litindex = consumeShort();

                    messageName = ctx.compiledBlock.literals[litindex];
                    stb = receiver.getSTClass().superClass.resolveMethod(messageName);
                    if (stb.isClassMethod && !(receiver instanceof STMetaClassObject)) {
                        error("ClassMessageSentToInstance", messageName + " is a class method sent to instance of " + receiver.getSTClass().getName());
                    } else if (!stb.isClassMethod && receiver instanceof STMetaClassObject) {
                        error("MessageNotUnderstood", messageName + " is an instance method sent to class object " + receiver.getSTClass().getName());
                    }
                    if (stb.isPrimitive()){
                        STObject result = stb.primitive.perform(ctx,nargs);
                        if (result != null){
                            ctx.push(result);
                        }
                    }else{
                        BlockContext curCtx = new BlockContext(this, stb, receiver);
                        for(int j=nargs-1; j>=0; j--){
                            curCtx.locals[j] = ctx.pop();
                        }
                        ctx.pop();
                        pushContext(curCtx);
                    }
                    break;
                case Bytecode.BLOCK:
                    index = consumeShort();
                    blk = ctx.enclosingMethodContext;
                    ctx.push(new BlockDescriptor(blk.compiledBlock.blocks[index], ctx));
                    break;
                case Bytecode.BLOCK_RETURN:
                    STObject br = ctx.pop();
                    popContext();
                    ctx.push(br);
                    break;
                case Bytecode.RETURN:
                    STObject r = ctx.pop();
                    //deal with double return!!!
                    if (ctx.enclosingMethodContext.enclosingContext != BlockContext.RETURNED) {
                        ctx = ctx.enclosingMethodContext;
                        ctx.enclosingContext = BlockContext.RETURNED;
                        popContext();
                        if (ctx == null) {
                            return r;
                        } else {
                            ctx.push(r);
                        }
                    }else {
//                        "BlockCannotReturn: T>>f-block0 can't trigger return again from method T>>f\n" +
//                                "    at                                    f>>f-block0[][](<string>:2:9)       executing 0012:  return           \n" +
//                                "    at                             MainClass>>main[a T][](<string>:6:3)       executing 0052:  send           0, 'value'\n";
                        error("BlockCannotReturn", ctx.compiledBlock.enclosingClass.getName() + ">>" +
                                ctx.compiledBlock.name + " can't trigger return again from method " +
                                ctx.enclosingMethodContext.compiledBlock.qualifiedName);
                    }
                    break;
                case Bytecode.DBG:
                    d = consumeShort();
                    i = consumeInt();
                    ctx.currentFile = ctx.compiledBlock.literals[d];
                    ctx.currentCharPos = Bytecode.charPosFromCombined(i);
                    ctx.currentLine = Bytecode.lineFromCombined(i);
                    break;
                default:
                    break;
			}
			if ( trace ) traceStack(); // show stack *after* execution
		}
		return ctx!=null ? ctx.receiver : null;
	}

    //self
    public void assertNumOperands(int i) {
        assert ctx.sp >= i-1;
    }

	public void error(String type, String msg) throws VMException {
		error(type, null, msg);
	}

	public void error(String type, Exception e, String msg) throws VMException {
		String stack = getVMStackString();
		switch ( type ) {
			case "MessageNotUnderstood":
				throw new MessageNotUnderstood(msg,stack);
			case "ClassMessageSentToInstance":
				throw new ClassMessageSentToInstance(msg, stack);
			case "IndexOutOfRange":
				throw new IndexOutOfRange(msg,stack);
			case "BlockCannotReturn":
				throw new BlockCannotReturn(msg,stack);
			case "StackUnderflow":
				throw new StackUnderflow(msg,stack);
			case "UndefinedGlobal":
				throw new UndefinedGlobal(msg,stack);
			case "MismatchedBlockArg":
				throw new MismatchedBlockArg(msg,stack);
			case "InternalVMException":
				throw new InternalVMException(e,msg,stack);
			case "UnknownClass":
				throw new UnknownClass(msg,stack);
			case "TypeError":
				throw new TypeError(msg,stack);
			case "UnknownField":
				throw new UnknownField(msg,stack);
			default :
				throw new VMException(msg,stack);
		}
	}

	public void error(String msg) throws VMException {
		error("unknown", msg);
	}

	public void pushContext(BlockContext ctx) {
		ctx.invokingContext = this.ctx;
		this.ctx = ctx;
	}

	public void popContext() { ctx = ctx.invokingContext; }

	public static STObject TranscriptStream_SHOW(BlockContext ctx, int nArgs, Primitive primitive) {
		VirtualMachine vm = ctx.vm;
		vm.assertNumOperands(nArgs + 1); // ensure args + receiver
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiverObj = ctx.stack[firstArg - 1];
		vm.assertEqualBackingTypes(receiverObj, "TranscriptStream");
		STObject arg = ctx.stack[firstArg];
		System.out.println(arg.asString());
		ctx.sp -= nArgs + 1; // pop receiver and arg
		return receiverObj;  // leave receiver on stack for primitive methods
	}

    public void assertEqualBackingTypes(STObject receiverObj, String transcriptStream){
        STObject sto = systemDict.lookupClass(transcriptStream);
        assert receiverObj.metaclass == sto;
    }

	public STMetaClassObject lookupClass(String id) {
		return systemDict.lookupClass(id);
	}

	public STObject newInstance(String className, Object ctorArg) {
		return null;
	}

	public STObject newInstance(STMetaClassObject metaclass, Object ctorArg) {
		return null;
	}

	public STObject newInstance(STMetaClassObject metaclass) {
		return new STObject(metaclass);
	}

	public STInteger newInteger(int v) {
        return new STInteger(this,v);
	}

	public STFloat newFloat(float v) {
		return new STFloat(this,v);
	}

    public STCharacter newChar(int c) {
        return new STCharacter(this,c);
    }

	public STString newString(String s) {
        int index=-1;
        for(int i =0; i<ctx.compiledBlock.literals.length;i++){
            if(ctx.compiledBlock.literals[i].equals(s)){
                index = i;
            }
        }
        STString sts;
        // there is same string
        if(index!=-1){
            if(ctx.compiledBlock.literalsAsSTStrings[index] == null) {
                ctx.compiledBlock.literalsAsSTStrings[index] = new STString(this, s);
            }
            sts = ctx.compiledBlock.literalsAsSTStrings[index];
        }else {
            sts = new STString(this, s);
        }
        return sts;
        //return new STString(this,s);
	}

	public STBoolean newBoolean(boolean b) {
        return new STBoolean(this,b);
	}

    public STArray newArray(VirtualMachine vm){
        int n = consumeShort();
        STObject[] stos = new STObject[n];
        for (int i = 0; i<n; i++) {
            stos[n - i - 1] = ctx.pop();
        }
        return new STArray(vm, stos);
    }

	public STNil nil() {
        //return new STNil(this);
        return (STNil) systemDict.lookup("NIL");
	}

	public int consumeShort() {
		int x = getShort(ctx.ip);
		ctx.ip += Bytecode.OperandType.SHORT.sizeInBytes;
		return x;
	}

    public int consumeInt(){
        int x = getInt(ctx.ip);
        ctx.ip += Bytecode.OperandType.INT.sizeInBytes;
        return x;
    }

    public float consumeFloat(){
        int x = getInt(ctx.ip);
        ctx.ip += Bytecode.OperandType.FLOAT.sizeInBytes;
        return Float.intBitsToFloat(x);
    }


    public int getInt(int index) {
        byte[] code = ctx.compiledBlock.bytecode;
        return Bytecode.getInt(code, index);
    }

	// get short operand out of bytecode sequence
	public int getShort(int index) {
		byte[] code = ctx.compiledBlock.bytecode;
		return Bytecode.getShort(code, index);
	}
	// D e b u g g i n g

	void trace() {
		traceInstr();
		traceStack();
	}

	void traceInstr() {
		String instr = Bytecode.disassembleInstruction(ctx.compiledBlock, ctx.ip);
		System.out.printf("%-40s", instr);
	}

	void traceStack() {
		BlockContext c = ctx;
		List<String> a = new ArrayList<>();
		while ( c!=null ) {
			a.add( c.toString() );
			c = c.invokingContext;
		}
		Collections.reverse(a);
		System.out.println(Utils.join(a, ", "));
	}

	public String getVMStackString() {
		StringBuilder stack = new StringBuilder();
		BlockContext c = ctx;
		while ( c!=null ) {
			int ip = c.prev_ip;
			if ( ip<0 ) ip = c.ip;
			String instr = Bytecode.disassembleInstruction(c.compiledBlock, ip);
			String location = c.currentFile+":"+c.currentLine+":"+c.currentCharPos;
			String mctx = c.compiledBlock.qualifiedName + pLocals(c) + pContextWorkStack(c);
			String s = String.format("    at %50s%-20s executing %s\n",
									 mctx,
									 String.format("(%s)",location),
									 instr);
			stack.append(s);
			c = c.invokingContext;
		}
		return stack.toString();
	}

	public String pContextWorkStack(BlockContext ctx) {
		StringBuilder buf = new StringBuilder();
		buf.append("[");
		for (int i=0; i<=ctx.sp; i++) {
			if ( i>0 ) buf.append(", ");
			pValue(buf, ctx.stack[i]);
		}
		buf.append("]");
		return buf.toString();
	}

	public String pLocals(BlockContext ctx) {
		StringBuilder buf = new StringBuilder();
		buf.append("[");
		for (int i=0; i<ctx.locals.length; i++) {
			if ( i>0 ) buf.append(", ");
			pValue(buf, ctx.locals[i]);
		}
		buf.append("]");
		return buf.toString();
	}

	public void pValue(StringBuilder buf, STObject v) {
		if ( v==null ) buf.append("null");
		else if ( v==nil() ) buf.append("nil");
		else if ( v instanceof STString) buf.append("'"+v.asString()+"'");
		else if ( v instanceof BlockDescriptor) {
			BlockDescriptor blk = (BlockDescriptor) v;
			buf.append(blk.block.name);
		}
		else if ( v instanceof STMetaClassObject ) {
			buf.append(v.toString());
		}
		else {
			STObject r = v.asString(); //getAsString(v);
			buf.append(r.toString());
		}
	}
}
