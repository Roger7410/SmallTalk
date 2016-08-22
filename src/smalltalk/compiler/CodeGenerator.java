package smalltalk.compiler;

import org.antlr.symtab.Scope;
import org.antlr.symtab.StringTable;
import org.antlr.symtab.Symbol;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.NotNull;
import smalltalk.misc.Utils;
import smalltalk.parser.*;
import smalltalk.vm.primitive.Primitive;
import smalltalk.vm.primitive.STCompiledBlock;
import smalltalk.vm.primitive.STString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fill STBlock, STMethod objects in Symbol table with bytecode,
 * {@link smalltalk.vm.primitive.STCompiledBlock}.
 */
public class CodeGenerator extends SmalltalkBaseVisitor<Code> {
	public Scope currentScope;

 //   public int test = 0;

	/** With which compiler are we generating code? */
	public final Compiler compiler;

    //keep block and its Strings
    //method to put
	public final Map<Scope,StringTable> blockToStrings = new HashMap<>();

	public CodeGenerator(Compiler compiler) {
		this.compiler = compiler;
        currentScope = compiler.symtab.GLOBALS;
	}

    boolean debug = false;

	/** This and defaultResult() critical to getting code to bubble up the
	 *  visitor call stack when we don't implement every method.
	 */
	@Override
	protected Code aggregateResult(Code aggregate, Code nextResult) {
		if ( aggregate!=Code.None ) {
			if ( nextResult!=Code.None ) {
				return aggregate.join(nextResult);
			}
			return aggregate;
		}
		else {
			return nextResult;
		}
	}

	@Override
	protected Code defaultResult() {
		return Code.None;
	}

	@Override
	public Code visitFile(@NotNull SmalltalkParser.FileContext ctx) {
        if (debug){
            System.out.println("Visit File.");
        }
        // if there is main -> visit
//        System.out.println(ctx.main().getText());
//        if (ctx.main() != null){
//            System.out.println("test ctx.main null");
//        }
//        if (ctx.main().getText().isEmpty()){
//            System.out.println("test ctx.main isEmpty");
//        }
        if (!ctx.main().getText().isEmpty()){ //fix
            visitMain(ctx.main());
        }
        // classes
        if (ctx.classDef() != null){
            for (int i = 0; i < ctx.classDef().size(); i++){
                visitClassDef(ctx.classDef(i));
            }
        }
        //System.out.println("test");
        return Code.None;
	}

    @Override
    public Code visitMain(SmalltalkParser.MainContext ctx) {
        if (debug){
            System.out.println("Visit Main.");
        }
        pushScope(ctx.classScope);
        pushScope(ctx.scope);
        Code code = visitChildren(ctx);

        // add DBG visitMain. At the end of the body, before pop, self, return.
        if (ctx.body() instanceof  SmalltalkParser.FullBodyContext){
            if (compiler.genDbg){
                //System.out.println(compiler.getFileName());
                dealBlockToStrings(compiler.getFileName());
                code = Code.join(code, dbgAtEndMain(ctx.stop));
            }
            // pop final value unless block is empty
            code = code.join(Compiler.pop());
        }
        // always add ^self in case no return statement
        code = code.join(Compiler.push_self());
        code = code.join(Compiler.method_return());
//        if(ctx.scope == null){
//            System.out.println(" hello !!!");
//        }
        ctx.scope.compiledBlock = getCompiledBlock(ctx.scope, code);
        popScope();
        popScope();
        return code;
    }

	@Override
	public Code visitClassDef(@NotNull SmalltalkParser.ClassDefContext ctx) {
        if (debug){
            System.out.println("Visit ClassDef.");
        }
		pushScope(ctx.scope);
		//Code code = visitChildren(ctx);
        visitChildren(ctx);
		popScope();
		return Code.None;
	}

    //'<' 'primitive:' SYMBOL '>'
    //SYMBOL # ID
    //+ y <primitive:#Integer_ADD>
    @Override
	public Code visitPrimitiveMethodBlock(@NotNull SmalltalkParser.PrimitiveMethodBlockContext ctx) {
        if (debug){
            System.out.println("Visit PrimitiveMethodBlock.");
        }
        SmalltalkParser.MethodContext methodNode = (SmalltalkParser.MethodContext)ctx.getParent();
        pushScope(methodNode.scope);
        Code code = visitChildren(ctx);
        //no DBG here
        if (compiler.genDbg) {
            dealBlockToStrings(compiler.getFileName());
            code = Code.join(code, dbgAtEndBlock(ctx.stop));
        }
//        //mark ? not using now
//		STPrimitiveMethod p = (STPrimitiveMethod)currentScope.resolve(ctx.selector);
//		STCompiledBlock blk = new STCompiledBlock(p);
//		String primitiveName = ctx.SYMBOL().getText(); // e.g., Integer_ADD
//		Primitive primitive = Primitive.valueOf(primitiveName);
        methodNode.scope.compiledBlock = getCompiledBlock(methodNode.scope, code);
        popScope();
		return Code.None;
	}

	@Override
	public Code visitSmalltalkMethodBlock(@NotNull SmalltalkParser.SmalltalkMethodBlockContext ctx) {
        if (debug){
            System.out.println("Visit visitSmalltalkMethodBlock.");
        }
		SmalltalkParser.MethodContext methodNode = (SmalltalkParser.MethodContext)ctx.getParent();
        pushScope(methodNode.scope);
//		System.out.println("Gen code for " + methodNode.scope.getName()+" "+getProgramSourceForSubtree(ctx));
		Code code = visitChildren(ctx);

		// always add ^self in case no return statement
        //DBG After visiting the children, before the pop, self,
		if ( compiler.genDbg ) { // put dbg in front of push_self
            dealBlockToStrings(compiler.getFileName());
			code = Code.join(code, dbgAtEndBlock(ctx.stop));
		}
		if ( ctx.body() instanceof SmalltalkParser.FullBodyContext ) {
			// pop final value unless block is empty
			code = code.join(Compiler.pop()); // visitFullBody() doesn't have last pop; we toss here but use with block_return in visitBlock
		}
		code = code.join(Compiler.push_self());
		code = code.join(Compiler.method_return());
		methodNode.scope.compiledBlock = getCompiledBlock(methodNode.scope, code);
//		System.out.println(Bytecode.disassemble(methodNode.scope.compiledMethod, 0));

        popScope();
		return Code.None;
	}

	public STCompiledBlock getCompiledBlock(STBlock blk, Code code) {
        if (debug){
            System.out.println("GetCompiledBlock Run.");
        }
		STCompiledBlock compiledBlock = new STCompiledBlock(blk);
        if (code != null){
            /////// mark!!!!! bytecode !!!!!!!!
            compiledBlock.bytecode = code.bytes();
        }
        if (blockToStrings.containsKey(blk)){//that scope
            //pass StringTable with key (blk)scope to compiledBlock->literals
            //debug
//            System.out.println(blockToStrings.containsKey(blk));
//            String[] strssss = blockToStrings.get(blk).toArray();
//            for (String strss : strssss){
//                System.out.println(strss);
//            }
            compiledBlock.literals = blockToStrings.get(blk).toArray(); //problem is blockToStrings.get(blk).toArray() is null
            if(debug) {
                for (int i = 0; i < compiledBlock.literals.length; i++) {
                    System.out.println(compiledBlock.literals[i]);
                }
            }
            //init literalsAsStrings in comliledBlock
            compiledBlock.literalsAsSTStrings = new STString[compiledBlock.literals.length];
        }

		return compiledBlock;


	}

    @Override
    public Code visitBlock(SmalltalkParser.BlockContext ctx) {
        if (debug) {
            System.out.println("Visit Block!");
        }
        pushScope(ctx.scope);

        Code indexCode = new Code();
        short bIndex = (short)ctx.scope.index;
        indexCode.join(Compiler.block(bIndex));
//        indexCode.join(Compiler.block(test));
//        test++;
//        if( bIndex == 0 ){
//            indexCode = indexCode.join(Compiler.block(0));
//            System.out.println("i am 0");
//        }
//        if( bIndex == 1){
//            indexCode = indexCode.join(Compiler.block(1));
//            System.out.println("i am 1");
//        }
//        int i = 1;
//        System.out.println(test);
        //System.out.println("local"+ctx.scope.numNestedBlocks);

        Code code = visitChildren(ctx);
        //After you join code for visitChildren()
        if (ctx.body() instanceof SmalltalkParser.EmptyBodyContext){
            code = code.join(Compiler.push_nil());
        }
        if (compiler.genDbg){
            dealBlockToStrings(compiler.getFileName());
            code = Code.join(code, dbgAtEndBlock(ctx.stop));
        }

        //Before you join push_block_return
        code = code.join(Compiler.block_return());
        ctx.scope.compiledBlock = getCompiledBlock(ctx.scope, code);

        popScope();
        return indexCode;
    }

    @Override
    public Code visitFullBody(SmalltalkParser.FullBodyContext ctx) {
        if (debug) {
            System.out.println("Visit FullBody!");
        }
        //mark
        Code code = new Code();
        List<SmalltalkParser.StatContext> stats = ctx.stat();
        for (int i = 0; i < stats.size(); i++) {
            code = code.join(visit(ctx.stat(i)));
            if (i < stats.size() - 1)
                code = code.join(Compiler.pop());
        }
        return code;
    }

    @Override
    public Code visitEmptyBody(SmalltalkParser.EmptyBodyContext ctx) {
        if (debug) {
            System.out.println("Visit EmptyBody!");
        }
        Code code = new Code();
        //At the end
        if(compiler.genDbg){
            dealBlockToStrings(compiler.getFileName());
            code = Code.join(code, dbgAtEndBlock(ctx.stop));
        }
        //Before you return
        //mark
        //code.join(Compiler.push_nil());
        //code.join(Compiler.block_return());
        return code;
    }

    @Override
	public Code visitAssign(@NotNull SmalltalkParser.AssignContext ctx) {
        if (debug) {
            System.out.println("Visit Assign!");
        }
		Code e = visit(ctx.messageExpression());
		Code store = store(ctx.lvalue().ID().getText());
		Code code = e.join(store);
		if ( compiler.genDbg ) {
            dealBlockToStrings(compiler.getFileName());
			code = dbg(ctx.start).join(code);
		}

		return code;
	}

	@Override
	public Code visitReturn(@NotNull SmalltalkParser.ReturnContext ctx) {
        if (debug) {
            System.out.println("Visit Return!");
        }
        //bug
		Code e = visit(ctx.messageExpression());
		if ( compiler.genDbg ) {
            dealBlockToStrings(compiler.getFileName());
			e = Code.join(e, dbg(ctx.start)); // put dbg after expression as that is when it executes
		}
		e.join(Compiler.method_return());
		return e;
	}

//    @Override
//    public Code visitSendMessage(SmalltalkParser.SendMessageContext ctx) {
//        return super.visitSendMessage(ctx);
//    }
    //keywordsend superkeywordsend

    @Override
    public Code visitKeywordSend(SmalltalkParser.KeywordSendContext ctx) {
        if (debug) {
            System.out.println("Visit KeywordSend!");
        }
        //recv=binaryExpression ( KEYWORD args+=binaryExpression )*
        Code code = visit(ctx.recv);
        Code args = new Code();
        if (ctx.args.size() != 0){
            for(int i = 0; i < ctx.args.size(); i++){
                args.join(visit(ctx.args.get(i)));
            }
        }

        if (ctx.KEYWORD().size() != 0){
            String str = "";
            for (int i = 0; i < ctx.KEYWORD().size(); i++){
                str += ctx.KEYWORD(i).getText();
            }


            if(compiler.genDbg){
                dealBlockToStrings(compiler.getFileName());
                args = Code.join(args, dbg(ctx.KEYWORD(0).getSymbol()));
            }
            //str always after filename
            dealBlockToStrings(str);
            //mark !!!
            int argIndex = getLiteralIndex(str);
            //After getLiteralIndex()

            //Before you join code for Send
            int argSize = ctx.args.size();
            args.join(Compiler.send(argSize, argIndex));
        }
        //join
        aggregateResult(code, args);
        return code;
    }

    @Override
    public Code visitSuperKeywordSend(SmalltalkParser.SuperKeywordSendContext ctx) {
        if (debug) {
            System.out.println("Visit SuperKeywordSend!");
        }
        //'super' ( KEYWORD args+=binaryExpression )+
        Code code = new Code();
        if (ctx.args.size() != 0) {
            for (int i = 0; i < ctx.args.size(); i++) {
                code = code.join(visit(ctx.args.get(i)));
            }
        }
        if (ctx.KEYWORD().size() != 0) {
            String str = "";
            for (int i = 0; i < ctx.KEYWORD().size(); i++){
                str += ctx.KEYWORD(i).getText();
            }
            dealBlockToStrings(str);
            //mark
            int argIndex = getLiteralIndex(str);
            int argSize = ctx.args.size();
            code.join(Compiler.send(argSize, argIndex));
        }
        return code;
    }

    @Override
    public Code visitBinaryExpression(SmalltalkParser.BinaryExpressionContext ctx) {
        if (debug) {
            System.out.println("Visit BinaryExpression!");
        }
        //unaryExpression ( bop unaryExpression )*
        Code code =  visit(ctx.unaryExpression(0));
        if (ctx.bop().size() != 0){
            String str;
            for (int i = 1 ; i <= ctx.bop().size();i++){
                code = aggregateResult(code, visit(ctx.unaryExpression(i)));
                //After you join code for visitUnaryExpression(1)
                if(compiler.genDbg){
                    //mark
                    dealBlockToStrings(compiler.getFileName());
                    code = Code.join(dbg(ctx.bop(i-1).getStart()), code);
                }
                str = ctx.bop().get(i-1).getText();
                dealBlockToStrings(str);
                int index = getLiteralIndex(str);
                //Before you join code for Send
                code = aggregateResult(code,Compiler.send(1,index));
            }
        }
        return code;
    }

    @Override
    public Code visitUnaryMsgSend(SmalltalkParser.UnaryMsgSendContext ctx) {
        if (debug) {
            System.out.println("Visit UnaryMsgSend!");
        }
        //unaryExpression ID
        //mark
        Code code = visit(ctx.unaryExpression());
        String str = ctx.ID().getText();
        //At the end

        if (compiler.genDbg) {
            //System.out.println(compiler.getFileName());
            dealBlockToStrings(compiler.getFileName());
            //code = Code.join(dbg(ctx.ID().getSymbol()), code);
            code = Code.join(dbg(ctx.stop),code);
        }
        dealBlockToStrings(str);
        //Before return
        int index = getLiteralIndex(str);
        code.join(Compiler.send(0,index));
        return code;

    }

    @Override
    public Code visitUnarySuperMsgSend(SmalltalkParser.UnarySuperMsgSendContext ctx) {
        if (debug) {
            System.out.println("Visit UnarySuperMsgSend!");
        }
        //'super' ID
        Code code = new Code();
        String str = ctx.ID().getText();
        dealBlockToStrings(str);
        int index = getLiteralIndex(str);
        code.join(Compiler.push_self()).join(Compiler.send_super(0, index));
        return code;
    }

    @Override
    public Code visitLiteral(SmalltalkParser.LiteralContext ctx) {
        if (debug) {
            System.out.println("Visit Literal!");
        }
        Code code = new Code();
        if (ctx.NUMBER() != null){
            String num = ctx.NUMBER().getText();
            if( num.contains(".") ){//float
                float f = Float.parseFloat(num);
                code.join(Compiler.push_float(f));
            }else {//int
                int i = Integer.parseInt(num);
                code.join(Compiler.push_int(i));
            }
        } else if (ctx.CHAR() != null){
            char c = ctx.CHAR().getText().charAt(1);
            code.join(Compiler.push_char(c));
        } else if (ctx.STRING() != null ){
            String s = ctx.STRING().getText();//FIX
            //mark
            if (compiler.genDbg) {
                dealBlockToStrings(compiler.getFileName());
            }
            dealBlockToStrings(s);
            int index = getLiteralIndex(s);
            code.join(Compiler.push_literal(index));
        } else {
            String s = ctx.getText();
            switch (s) {
                case "nil":
                    code.join(Compiler.push_nil());
                    break;
                case "self":
                    code.join(Compiler.push_self());
                    break;
                case "true":
                    code.join(Compiler.push_true());
                    break;
                case "false":
                    code.join(Compiler.push_false());
                    break;
                default:
                    break;
            }
        }
        return code;
    }

    @Override
    public Code visitArray(SmalltalkParser.ArrayContext ctx) {
        if (debug) {
            System.out.println("Visit Array!");
        }
        Code code = visitChildren(ctx);
        code.join(Compiler.push_array(ctx.messageExpression().size()));
        return code;
    }

    @Override
    public Code visitId(SmalltalkParser.IdContext ctx) {
        if (debug) {
            System.out.println("Visit ID!");
        }
        Code code = new Code();
        code.join(push(ctx.getText()));
        return code;
    }

    public void pushScope(Scope scope) {
		currentScope = scope;
	}

	public void popScope() {
        if(debug) {
            if (currentScope.getEnclosingScope() != null) {
                System.out.println("popping from " + currentScope.getName() + " to " + currentScope.getEnclosingScope().getName());
            } else {
                System.out.println("popping from " + currentScope.getName() + " to null");
            }
        }
		currentScope = currentScope.getEnclosingScope();
	}

	public int getLiteralIndex(String s) {
        if(blockToStrings.containsKey(currentScope)) {
            StringTable st = blockToStrings.get(currentScope);
            String[] strs = st.toArray();
//            int index = 0;
//            for (String str : strs){
//                if(str.equals(s)){
//                    return index;
//                }
//                index++;
//            }
            for (int i = 0; i < strs.length; i++) {
                //cause we cut ' ' in deal with BlockToString
                //tmp resolve method
                //or add index in dealBTS
                if (strs[i].equals(s) || s.equals("'"+strs[i]+"'")) {
                    return i;
                }
            }
        }
        return -1;
	}

    //save all block text to stringTables
    //Scope StringTable   pairs
    public void dealBlockToStrings(String str){
        // \' ??
        if (debug) {
            System.out.println("DEALBLOCKTOSTRINGS");
            System.out.println(str);
        }
        if (str.contains("'"))
            str = str.substring(str.indexOf("'") + 1, str.lastIndexOf("'"));
        if( blockToStrings.get(currentScope) != null){
            blockToStrings.get(currentScope).add(str);
        }else{//create
            StringTable st = new StringTable();
            st.add(str);//!!!!!!!!!!!!!!! noob!!!
            blockToStrings.put(currentScope, st);
        }
    }

    public Code store(String varName) {
        Code code = new Code();
        Symbol var = currentScope.resolve(varName);
        if ( var instanceof STField ) {
            code.join(Compiler.store_field(var.getInsertionOrderNumber()));
        }
        else if ( var instanceof STVariable ) {
            // store_local i
            int i = var.getInsertionOrderNumber();
            // this is really the delta from current scope to var.scope
            int d = ((STBlock) currentScope).getRelativeScopeCount(var.getScope().getName());
            code.join(Compiler.store_local(d, i));
        }
        else {//mark
            // store_local i
//            int i = var.getInsertionOrderNumber();
//            // this is really the delta from current scope to var.scope
//            int d = ((STBlock) currentScope).getRelativeScopeCount(var.getScope().getName());
//            code.join(Compiler.store_local(d, i));
            return Code.None;
        }
        return code;
    }

    public Code push(String varName){
        Code code = new Code();
        Symbol var = currentScope.resolve(varName);
        //global
        if (var == null || var.getScope() == compiler.symtab.GLOBALS) {

            if(compiler.genDbg) {
                dealBlockToStrings(compiler.getFileName());
            }
            dealBlockToStrings(varName);
            int index = getLiteralIndex(varName);
            code.join(Compiler.push_global(index));
        }else {
        //field  local
            if (var instanceof STField) {
                //getLocalIndex
//                int i = ((STBlock) currentScope).getLocalIndex(varName);
//                code.join(Compiler.push_field(i));
//                var.setInsertionOrderNumber(1);

//                int index = var.getInsertionOrderNumber();
                //get superClass  add + count
                code.join(Compiler.push_field(var.getInsertionOrderNumber()));
//                index++;
//                var.setInsertionOrderNumber(index);
            } else {
                int i = var.getInsertionOrderNumber();
                int d = ((STBlock) currentScope).getRelativeScopeCount(var.getScope().getName());
                code.join(Compiler.push_local(d, i));
            }
        }
        return code;
    }

	public Code dbgAtEndMain(Token t) {
		int charPos = t.getCharPositionInLine() + t.getText().length();
		return dbg(t.getLine(), charPos);
	}

	public Code dbgAtEndBlock(Token t) {
		int charPos = t.getCharPositionInLine() + t.getText().length();
		charPos -= 1; // point at ']'
		return dbg(t.getLine(), charPos);
	}

	public Code dbg(Token t) {
		return dbg(t.getLine(), t.getCharPositionInLine());
	}

	public Code dbg(int line, int charPos) {
		return Compiler.dbg(getLiteralIndex(compiler.getFileName()), line, charPos);
	}
}
