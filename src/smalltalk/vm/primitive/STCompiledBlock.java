package smalltalk.vm.primitive;

import org.antlr.symtab.Scope;
import org.antlr.symtab.Utils;
import org.stringtemplate.v4.ST;
import smalltalk.compiler.STBlock;
import smalltalk.compiler.STMethod;
import smalltalk.compiler.STPrimitiveMethod;
import smalltalk.vm.Bytecode;

import java.util.List;

/** This object represents the compiled code for a block or method.
 *  It holds all of the bytecode and meta information about the block, such
 *  as the number of arguments and the number of local variables.
 *
 *  If this object is a placeholder for a primitive method, field primitive
 *  will be non-null.
 *
 *  If this object represents a compiled method, field blocks will be
 *  an array of pointers to the compiled code for all nested blocks of the
 *  method.
 *
 *  All blocks can reference literals method names and string literals ref'd within.
 *  These are stored in the literals field. The associated STString objects
 *  are cached in literalsAsSTStrings.
 *
 *  This object has a reference to the lexically enclosing class' metaclass,
 *  which is set by {@link STMetaClassObject} during construction.
 */
public class STCompiledBlock {
	public static final String testStringTemplate =
		"name: <name>\n" +
		"qualifiedName: <qualifiedName>\n" +
		"nargs: <nargs>\n" +
		"nlocals: <nlocals>\n"+
		"literals: <literals:{s | '<s>'}; separator={,}>\n" +
		"<assembly>"+
		"<if(blocks)>" +
		"blocks:\n"+
		"    <blocks; separator={<\\n>}>" +
		"<endif>";

	/** currently unused, but would be used to serialize a compiled block to disk */
	public static final String serializeTemplate =
		"name: <name>\n" +
		"qualifiedName: <qualifiedName>\n" +
		"nargs: <nargs>\n" +
		"nlocals: <nlocals>\n"+
		"literals: <literals:{s | `<s>`}; separator={,}>\n" + // unique separator
		"bytecode: <bytecode; separator={ }>" +
		"<if(blocks)>" +
		"blocks:\n"+
		"    <blocks>" +
		"<endif>";

	/** The simple name for a block or method like at:put: or foo:-local0 */
	public String name;

	/** The fully qualified name for this block or method like foo>>x or T>>x */
	public String qualifiedName;

	/** Of which class is this block a member? */
	public STMetaClassObject enclosingClass;

	/** The set of strings and symbols referenced by the {@link #bytecode} field. */
	public String[]   literals;

	/** Cache STString objects for literals */
	public STString[] literalsAsSTStrings;

	/** The byte code instructions for this specific block, if not primitive. */
	public byte[]     bytecode;

	/** If this is a compiled method, not just a block, this is the list
	 *  of all nested blocks within the method. The BLOCK instruction refers to
	 *  them by unique integer and finds them by indexing into this array.
	 *  The outermost method block is blocks[0].
	 *
	 *  This is unused for [...] blocks (i.e., not methods).
 	 */
	public STCompiledBlock[] blocks;

	/** The fixed number of arguments taken by this method */
	public final int nargs;

	/** The number of local variables defined within the block, not including the arguments */
	public final int nlocals;

	/** A pointer to the primitive implementing the method, if this field
	 *  is non-null.
 	 */
	public final Primitive primitive;

	/** True if method was defined as class method in Smalltalk */
	public final boolean isClassMethod;

	public STCompiledBlock(STBlock blk) {

		this.name = blk.getName();

		this.qualifiedName = blk.getQualifiedName(">>");
		nargs = blk.nargs();
		nlocals = blk.nlocals();
        if (blk instanceof STPrimitiveMethod){
            primitive = ((STPrimitiveMethod)blk).primitive;
        }else{
            primitive = null;
        }

        isClassMethod = blk.isMethod()&&((STMethod)blk).isClassMethod;
        if(isClassMethod){
            name = "static "+blk.getName();
        }

        //mark wired
        if(blk.isMethod()){
            //deal with all blocks
            List<Scope> STBlocks = blk.getAllNestedScopedSymbols();
            blocks = new STCompiledBlock[STBlocks.size()];
            for(int i=0; i<STBlocks.size();i++){
                STBlock stb = ((STBlock)STBlocks.get(i));
                blocks[stb.index] = stb.compiledBlock;
            }
        }

        //mark ?
		//isClassMethod = ((STMethod) blk).isClassMethod;
	}

	public boolean isPrimitive() { return primitive!=null; }

	public String toTestString() { return getAsString(testStringTemplate); }

	public String serialize() { return getAsString(serializeTemplate); }

	public String getAsString(String templateString) {
		ST template = new ST(templateString);
		template.add("name", name);
		template.add("qualifiedName", qualifiedName);
		template.add("nargs", nargs);
		template.add("nlocals", nlocals);
		template.add("literals", literals);
		template.add("bytecode", bytecode);
		template.add("assembly", Bytecode.disassemble(this, 0));
		template.add("blocks",
					 templateString==testStringTemplate ?
						 Utils.map(blocks, STCompiledBlock::toTestString) :
						 Utils.map(blocks, STCompiledBlock::serialize)
					);
		return template.render();
	}
}
