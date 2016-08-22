package smalltalk.compiler;

import org.antlr.symtab.Scope;
import org.antlr.symtab.Symbol;
import org.antlr.symtab.VariableSymbol;
import org.antlr.v4.codegen.*;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import smalltalk.misc.Utils;
import smalltalk.vm.Bytecode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import smalltalk.parser.*;
import smalltalk.vm.VirtualMachine;
import smalltalk.vm.primitive.STMetaClassObject;

public class Compiler {
	protected final STSymbolTable symtab;
	public final List<String> errors = new ArrayList<>();
	protected SmalltalkParser parser;
	protected CommonTokenStream tokens;
    ParserRuleContext tree;
    protected SmalltalkParser.FileContext fileTree;
	protected String fileName;
	public boolean genDbg; // generate dbg file,line instructions

	public Compiler() {
		symtab = new STSymbolTable();
        //fileName = "<unknown>";
	}

	public Compiler(STSymbolTable symtab) {
		this.symtab = symtab;
        //fileName = "<string>";
	}

	public String getFileName() {
		//return fileName
        //System.out.println(tokens.getSourceName());
        //return tokens.getSourceName();
        fileName = tokens.getSourceName();
        //for files
        fileName = fileName.substring(fileName.lastIndexOf('/')+1);
        return fileName;
	}

	public STSymbolTable compile(ANTLRInputStream input) {
		tree = parseClasses(input);
		if ( tree!=null ) {
			defSymbols(tree);
			resolveSymbols(tree);
			CodeGenerator gen = new CodeGenerator(this);
			gen.visit(tree);
		}

		return symtab;
	}

	public ParserRuleContext parseClasses(ANTLRInputStream input) {
		SmalltalkLexer lexer = new SmalltalkLexer(input);
		tokens = new CommonTokenStream(lexer);
		parser = new SmalltalkParser(tokens);
        tree = parser.file();
        fileTree = (SmalltalkParser.FileContext)tree;
		return tree;
	}

    //SYMBOL
	public void defSymbols(ParserRuleContext tree) {
		DefineSymbols defineSymbols = new DefineSymbols(this);
		ParseTreeWalker.DEFAULT.walk(defineSymbols, tree);
	}

	public void resolveSymbols(ParserRuleContext tree) {
		ResolveSymbols resolveSymbols = new ResolveSymbols(this);
		ParseTreeWalker.DEFAULT.walk(resolveSymbols, tree);
	}

    //define files args locals for define symbols
    public void defineFields(STClass cl, List<String> fields){
        if( fields != null){
            for (String f : fields){
                try{
                    STField sf = new STField(f);
                    cl.define(sf);
                }catch (IllegalArgumentException e){
                    error("redefinition of "+f+" in "+cl.toQualifierString(">>"));
                }
            }
        }
    }

    public void defineLocals(Scope currentScope, List<String> vars){
        if( vars != null){
            for (String v : vars){
                try{
                    STVariable sv = new STVariable(v);

                    currentScope.define(sv);
                }catch (IllegalArgumentException e){
                    //error("IllegalArgumentException");
                    error("redefinition of "+v+" in "+currentScope.toQualifierString(">>"));
                }
            }
        }
    }

    public void defineArguments(STBlock sb, List<String> args){
        if (args != null){
            for (String a : args){
                try{
                    STArg sa = new STArg(a);
                    sb.define(sa);
                }catch (IllegalArgumentException e){
                    //[redefinition of x in global>>T>>at:put:]
                    error("redefinition of "+a+" in "+sb.toQualifierString(">>"));
                }
            }
        }
    }

    //CODE GEN
    // Convenience methods for code gen
    //add getMetaObjects
//    public static List getMetaObjects(STSymbolTable s

    //methodt){
//        List list = new ArrayList<>();
//        return list;
//    }
    //mark pending
    public static List<STMetaClassObject> getMetaObjects(STSymbolTable symtab, VirtualMachine vm) {
        List<STMetaClassObject> metas = new ArrayList<>();
        for (Symbol s : symtab.GLOBALS.getSymbols()) {
            if ( s instanceof STClass) {
                metas.add(new STMetaClassObject(vm, (STClass)s));
            }
        }
        return metas;
    }

    //push
    public static Code push_nil() 				    { return Code.of(Bytecode.NIL); }
    public static Code push_self() 				    { return Code.of(Bytecode.SELF); }
    public static Code push_true() 				    { return Code.of(Bytecode.TRUE); }
    public static Code push_false() 			    { return Code.of(Bytecode.FALSE); }
    public static Code push_char(int c)			    { return Code.of(Bytecode.PUSH_CHAR).join(Utils.shortToBytes(c)); }
    public static Code push_int(int v) 			    { return Code.of(Bytecode.PUSH_INT).join(Utils.intToBytes(v)); }
    public static Code push_float(float f) 		    { return Code.of(Bytecode.PUSH_FLOAT).join(Utils.floatToBytes(f)); }
    public static Code push_array(int a) 		    { return Code.of(Bytecode.PUSH_ARRAY).join(Utils.shortToBytes(a)); }
    public static Code push_literal(int l)          { return Code.of(Bytecode.PUSH_LITERAL).join(Utils.toLiteral(l)); }

    public static Code push_global(int g) 		    { return Code.of(Bytecode.PUSH_GLOBAL).join(Utils.toLiteral(g)); }
    public static Code push_field(int f) 		    { return Code.of(Bytecode.PUSH_FIELD).join(Utils.toLiteral(f)); }
    public static Code push_local(int d, int i)     { return Code.of(Bytecode.PUSH_LOCAL).join(Utils.toLiteral(d).join(Utils.toLiteral(i))); }

    //pop
    public static Code pop() 					    { return Code.of(Bytecode.POP); }

    //store
    public static Code store_field(int f) 		    { return Code.of(Bytecode.STORE_FIELD).join(Utils.shortToBytes(f)); }
    public static Code store_local(int d, int i)    { return Code.of(Bytecode.STORE_LOCAL).join(Utils.shortToBytes(d)).join(Utils.shortToBytes(i)); }


    //public static Code method(short m)            { return Code.of(Bytecode.METHOD); }
    public static Code method_return() 			    { return Code.of(Bytecode.RETURN); }

    //send
    public static Code send(int size, int i)        { return Code.of(Bytecode.SEND).join(Utils.toLiteral(size).join(Utils.toLiteral(i))); }
    public static Code send_super(int size, int i)  { return Code.of(Bytecode.SEND_SUPER).join(Utils.toLiteral(size).join(Utils.toLiteral(i))); }

    //block
    public static Code block(short b) 			    { return Code.of(Bytecode.BLOCK).join(Utils.shortToBytes(b)); }
    public static Code block_return()               { return Code.of(Bytecode.BLOCK_RETURN); }

    //dbg
    public static Code dbg(int litIndex,
						   int line,
						   int charPos)
	{
        //mark   try Bytecode.combinLineCharPos(line,charPos)
        // 1 256 ?
//		return Code.of(Bytecode.DBG)
//			.join(Utils.toLiteral(litIndex))
//			.join(Utils.shortToBytes(line))
//			.join(Utils.shortToBytes(charPos));
        return Code.of(Bytecode.DBG)
                .join(Utils.toLiteral(litIndex)
                .join(Utils.intToBytes(Bytecode.combineLineCharPos(line,charPos))));
	}

	// Error support
	public void error(String msg) {
		errors.add(msg);
	}

	public void error(String msg, Exception e) {
		errors.add(msg+"\n"+ Arrays.toString(e.getStackTrace()));
	}

    //mark not useful now maybe not useful forever
//    public STMethod createMethod(String methodName, SmalltalkParser.MethodContext ctx) {
//        return new STMethod(methodName,ctx);
//    }

}
