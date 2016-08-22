package smalltalk.vm;

import smalltalk.compiler.STMethod;
import smalltalk.compiler.STSymbolTable;
import smalltalk.vm.primitive.STMetaClassObject;
import smalltalk.vm.primitive.STNil;
import smalltalk.vm.primitive.STObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stores predefined system objects like nil, true, false, Transcript as well
 *  as all {@link smalltalk.vm.primitive.STMetaClassObject}'s.  You should
 *  create just one instance of STNil for nil and an instance of STBoolean
 *  for true and for false.
 */
public class SystemDictionary {
	// All metaclass info and any predefined global objects like nil, true, ...
	protected final Map<String,STObject> objects = new LinkedHashMap<>();

	public final VirtualMachine vm;

	public SystemDictionary(VirtualMachine vm) {
		this.vm = vm;
	}

	/** Convert the symbol table with classes, methods, and compiled code
	 *  (as computed by the compiler) into a system dictionary that has
	 *  meta-objects.
	 *
	 *  This method assumes that the compiler has augmented the symbol table
	 *  symbols such as {@link STMethod} with pointers to the
	 *  {@link smalltalk.vm.primitive.STCompiledBlock}s.
	 */
	public void symtabToSystemDictionary(STSymbolTable symtab) {
	}

	/** Define predefined object Transcript. */
	public void initPredefinedObjects() {
        this.define("NIL",new STNil(vm));
	}

	public STObject lookup(String id) {
		return objects.get(id);
	}

    //superClass in STMetaClassObject
	public STMetaClassObject lookupClass(String id) {
        if(objects.get(id) != null){
            return (STMetaClassObject) (objects.get(id));
        }
		return null;
	}

	public void defineMetaObject(String name, STMetaClassObject meta) {
        objects.put(name,meta);
	}

	public Collection<STObject> getObjects() { return objects.values(); }

	public void define(String id, STObject v) { objects.put(id, v); }
}
