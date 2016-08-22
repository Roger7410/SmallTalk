package smalltalk.vm.primitive;

import org.antlr.symtab.FieldSymbol;
import org.antlr.symtab.MethodSymbol;
import org.stringtemplate.v4.ST;
import smalltalk.compiler.STClass;
import smalltalk.compiler.STMethod;
import smalltalk.vm.VirtualMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.antlr.symtab.Utils.map;

/** A meta class that has info about ST classes like Java's Class class.
 *
 *  Expose this as a (meta) object in VM but it has little functionality and
 *  is mainly for uniformity that classes are also objects.
 */
public class STMetaClassObject extends STObject {
	public final VirtualMachine vm;
	public String name;
	public STMetaClassObject superClass;

	public final List<String> fields;
	public final Map<String,STCompiledBlock> methods;

	public STMetaClassObject(VirtualMachine vm, STClass classSymbol) {
		super(null); // metaclass for a metaclass is 'this' but 'this' doesn't exist yet; see override of getSTClass()

		this.vm = vm;
        this.name = classSymbol.getName();
        //superClass = null; // fix me
        superClass = vm.systemDict.lookupClass(classSymbol.getSuperClassName());
//        if ( superClass != null ){
//            System.out.println(superClass.name);
//        }
        //classSymbol.setSuperClass(classSymbol.getSuperClassName());
        //mark
		fields = new ArrayList<>();
		// old --- make space for ALL fields, including inherited ones
        // new --- make space for only defined Fields. inherited is dealed in DefineSymbols
//		for (FieldSymbol f : classSymbol.getFields()) {
//			fields.add(f.getName());
//		}
        for (FieldSymbol f : classSymbol.getDefinedFields()) {
            fields.add(f.getName());
        }
		// for all methods defined in classSymbol, map method name to its compiled method
		methods = new HashMap<>();
		for (MethodSymbol m : classSymbol.getDefinedMethods()) {
			methods.put(m.getName(), ((STMethod)m).compiledBlock);
		}


        // set enclosingClass for all nested blocks within method
        for (STCompiledBlock blk1 : methods.values()){
            blk1.enclosingClass = this;
            for (STCompiledBlock blk2 : blk1.blocks){
                blk2.enclosingClass = this;
            }
        }
	}

	@Override
	public STMetaClassObject getSTClass() {
		return this;
	}

	public static STObject perform(BlockContext ctx, int nArgs, Primitive primitive) {
        //mark
		VirtualMachine vm = ctx.vm;
		ctx.vm.assertNumOperands(nArgs+1); // ensure args + receiver
		int firstArg = ctx.sp - nArgs + 1;
		STObject receiver = null;
		STObject result = vm.nil();
		//if ( firstArg-1 >= 0 )
		switch ( primitive ) {
			case Object_Class_BASICNEW:
                //mark
                receiver = ctx.stack[firstArg-1];
                ctx.sp--;
                result = vm.newInstance(receiver.getSTClass());
				break;
			case Object_Class_ERROR:
				vm.error(ctx.stack[firstArg].asString().toString());
				break;
		}
		return result;
	}

	public String getName() { return name; }

	public STCompiledBlock resolveMethod(String name) {
        //mark methods?

		STCompiledBlock blk = methods.get(name);
//        if ( name.equals("at:put:")){
//            blk = vm.systemDict.lookupClass("Array").methods.get(name);
//            superClass = vm.systemDict.lookupClass("Collection");
//        }
//        if(blk != null )
//            System.out.println(blk.name);
        if(blk == null){
//            if ( superClass != null) {
                blk = superClass.resolveMethod(name);
//            }else {
//                superClass = vm.systemDict.lookupClass("Collection");
//            }
        }
		return blk;
	}

	public int getNumberOfFields() {
		return fields.size();
	}

	public String toTestString() {
		ST template = new ST(
			"name: <name>\n" +
			"superClass: <superClass.name>\n" +
			"fields: <fields; separator={,}>\n" +
			"methods:\n" +
			"    <methods; separator={<\\n>}>"
		);
		template.add("name", name);
		template.add("superClass", superClass);
		template.add("fields", fields);
		template.add("methods", map(methods.values(), STCompiledBlock::toTestString));
		return template.render();
	}

	@Override
	public String toString() {
		return "class "+name;
	}
}
