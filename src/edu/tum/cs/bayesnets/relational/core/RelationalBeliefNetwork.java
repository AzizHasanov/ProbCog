package edu.tum.cs.bayesnets.relational.core;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;

import edu.ksu.cis.bnj.ver3.core.BeliefNode;
import edu.ksu.cis.bnj.ver3.core.CPF;
import edu.ksu.cis.bnj.ver3.core.Discrete;
import edu.ksu.cis.bnj.ver3.core.Domain;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.relational.core.RelationalNode.Signature;
import edu.tum.cs.mln.MLNWriter;
import edu.tum.cs.srldb.Database;

public class RelationalBeliefNetwork extends BeliefNetworkEx {
	/**
	 * maps a node index to the corresponding node
	 */
	protected HashMap<Integer,RelationalNode> relNodesByIdx;
	/**
	 * maps a function/predicate name to the signature of the corresponding function
	 */
	protected Map<String, Signature> signatures;
	
	public class RelationKey {
		/**
		 * the name of the relation
		 */
		public String relation;
		/**
		 * parameter indices that make up a key
		 */
		public Vector<Integer> keyIndices;
		
		public RelationKey(String relation, String[] arguments) {
			this.relation = relation;
			keyIndices = new Vector<Integer>();
			for(int i = 0; i < arguments.length; i++) {
				if(!arguments[i].equals("_")) {
					keyIndices.add(i);
				}
			}
		}
		
		public String toString() {
			return relation + "[" + keyIndices + "]";
		}
	}	
	/**
	 * a mapping of function/relation names to RelationKey objects which signify argument groups that are keys of the relation (which may be used for a functional lookup)
	 */
	protected Map<String, Collection<RelationKey>> relationKeys;
	/**
	 * a mapping of nodes to their corresponding parent grounders (which are created on demand)
	 */
	protected Map<RelationalNode, ParentGrounder> parentGrounders;
	
	public Collection<RelationKey> getRelationKeys(String relation) {
		return relationKeys.get(relation.toLowerCase());
	}
	
	public RelationalBeliefNetwork(String xmlbifFile) throws Exception {
		super(xmlbifFile);
		relNodesByIdx = new HashMap<Integer, RelationalNode>();		
		signatures = new HashMap<String, Signature>();
		relationKeys = new HashMap<String, Collection<RelationKey>>();
		parentGrounders = new HashMap<RelationalNode, ParentGrounder>();
		// store node data		
		BeliefNode[] nodes = bn.getNodes();
		for(int i = 0; i < nodes.length; i++) {
			RelationalNode d = new RelationalNode(this, nodes[i]);			
			addRelationalNode(d);
		}
	}
	
	public void addRelationalNode(RelationalNode node) {
		relNodesByIdx.put(node.index, node);
	}
	
	/**
	 * gets the first relational node where the entire node label matches the given name
	 * @param name
	 * @return
	 */
	public RelationalNode getRelationalNode(String name) {
		BeliefNode node = this.getNode(name);
		if(node == null)
			return null;
		return getRelationalNode(this.getNodeIndex(node));
	}
	
	public RelationalNode getRelationalNode(int idx) {
		return relNodesByIdx.get(new Integer(idx));
	}
	
	public RelationalNode getRelationalNode(BeliefNode node) {
		return getRelationalNode(this.getNodeIndex(node));
	}
	
	public Collection<RelationalNode> getRelationalNodes() {
		return relNodesByIdx.values();
	}
	
	public boolean isBooleanDomain(Domain domain) {
		if(!(domain instanceof Discrete))
			return false;
		int order = domain.getOrder();
		if(order > 2 || order <= 0)
			return false;
		if(domain.getOrder() == 1) {
			if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(0).equalsIgnoreCase("false"))
				return true;
			return false;
		}
		if(domain.getName(0).equalsIgnoreCase("true") || domain.getName(1).equalsIgnoreCase("true"))
			return true;
		return false;
	}
	
	/**
	 * obtains the names of parents of the variable that is given by a node name and its actual arguments
	 * @param nodeName 
	 * @param actualArgs
	 * @return an array of variable names
	 * @throws Exception
	 */
	public String[] getParentVariableNames(RelationalNode node, String[] actualArgs) throws Exception {
		RelationalNode child = node;
		BeliefNode[] parents = bn.getParents(child.node);
		String[] ret = new String[parents.length];
		for(int i = 0; i < parents.length; i++) {
			RelationalNode parent = getRelationalNode(getNodeIndex(parents[i].getName()));
			StringBuffer varName = new StringBuffer(parent.getFunctionName() + "(");
			String param = null;
			for(int iCur = 0; iCur < parent.params.length; iCur++) {
				for(int iMain = 0; iMain < child.params.length; iMain++) {
					if(child.params[iMain].equals(parent.params[iCur])) {
						param = actualArgs[iMain];
						break;
					}
				}
				if(param == null)
					throw new Exception(String.format("Could not determine parameters of parent '%s' for node '%s'", parent.getFunctionName(), node.getFunctionName() + actualArgs.toString()));
				varName.append(param);
				if(iCur < parent.params.length-1)
					varName.append(",");
			}
			varName.append(")");
			ret[i] = varName.toString();
		}
		return ret;
	}
	
	public void addSignature(String predicateName, Signature sig) {
		//relNodesByIdx.get(nodeIndex).sig = sig;
		signatures.put(predicateName.toLowerCase(), sig);
	}
	
	public void addSignature(RelationalNode node, Signature sig) {
		String name = node.getFunctionName().toLowerCase();
		/*if(node.isConstant)
			name = "__CONST" + node.index;*/
		signatures.put(name, sig);
	}
	
	public Signature getSignature(String predicateName) {
		return signatures.get(predicateName.toLowerCase());
	}
	
	public Signature getSignature(RelationalNode node) {
		/*if(node.isConstant) 
			return signatures.get("__CONST" + node.index);
		else*/
			return signatures.get(node.getFunctionName().toLowerCase());
	}
	
	public Collection<Signature> getSignatures() {
		return signatures.values();
	}
	
	/**
	 * replace a type by a new type in all node signatures
	 * @param oldType
	 * @param newType
	 */
	public void replaceType(String oldType, String newType) {
		for(RelationalNode node : this.relNodesByIdx.values()) {
			getSignature(node).replaceType(oldType, newType);
		}
	}
	
	/**
	 * guesses the model's function signatures by assuming the same type whenever the same variable name is used (ignoring any numeric suffixes), setting the domain name to ObjType_x when x is the variable,
	 * and assuming a different domain of return values for each node, using Dom{NodeName} as the domain name.
	 * @throws Exception 
	 *
	 */
	public void guessSignatures() throws Exception {
		for(RelationalNode node : relNodesByIdx.values()) {
			if(node.isConstant) // signatures for constants are determined in checkSignatures (called below)
				continue;
			String[] argTypes = new String[node.params.length];
			for(int i = 0; i < node.params.length; i++) {
				String param = node.params[i].replaceAll("\\d+", "");
				argTypes[i] = "ObjType_" + param;
			}
			String retType = isBooleanDomain(((Discrete)node.node.getDomain())) ? "Boolean" : "Dom" + node.getFunctionName(); 
			addSignature(node.getFunctionName(), new Signature(node.getFunctionName(), retType, argTypes));		
		}
		checkSignatures(); // to fill constants
	}
	
	/**
	 * check signatures for inconsistencies and write return types for constant nodes
	 * @throws Exception
	 */
	protected void checkSignatures() throws Exception {
		// obtain parameter/argument -> type name mapping for non-constant nodes
		HashMap<String,String> types = new HashMap<String,String>();
		Vector<RelationalNode> constants = new Vector<RelationalNode>();
		for(RelationalNode node : relNodesByIdx.values()) {
			if(node.isConstant)
				constants.add(node);
			else {
				Signature sig = getSignature(node);
				if(sig == null) {
					throw new Exception("Node " + node + " has no signature!");
				}
				// check for the right number of arguments and their types
				if(sig.argTypes.length != node.params.length)
					throw new Exception(String.format("Signature of '%s' does not match node definition: It contains %d elements vs. %d elements in the node definition.", node.toString(), sig.argTypes.length, node.params.length));
				for(int i = 0; i < node.params.length; i++) {
					String key = node.params[i];
					String value = types.get(key);
					if(value != null && !value.equals(sig.argTypes[i]))
						throw new Exception(String.format("Type mismatch: '%s' has types '%s' and '%s'", key, value, sig.argTypes[i]));
					types.put(key, sig.argTypes[i]);
				}
			}
		}			
		// update constant return types using the mapping
		for(RelationalNode constant : constants) {
			String type = types.get(constant.getFunctionName());
			if(type == null) // constants that were referenced by any of their parents must now have a type assigned
				throw new Exception("Constant " + constant + " not referenced and therefore not typed.");
			Signature sig = new Signature(constant.getFunctionName(), type, new String[0]);
			addSignature(constant, sig);
		}
	}

	public RelationalNode[] getRelationalParents(RelationalNode node) {
		BeliefNode[] p = this.bn.getParents(node.node);
		RelationalNode[] p2 = new RelationalNode[p.length];
		for(int i = 0; i < p.length; i++) {
			p2[i] = getRelationalNode(p[i]);
		}
		return p2;
	}
	
	public void toMLN(PrintStream out, boolean compactFormulas) throws Exception {
		MLNWriter writer = new MLNWriter(out);
		
		// write domain declarations
		out.println("// domain declarations");
		HashSet<String> handled = new HashSet<String>();
		HashMap<String, Vector<String>> domains = new HashMap<String,Vector<String>>();
		for(RelationalNode node : getRelationalNodes()) {	
			Signature sig = getSignature(node.getFunctionName());
			if(sig.returnType.equals("Boolean"))
				continue;
			if(handled.contains(sig.returnType))
				continue;
			handled.add(sig.returnType);
			Vector<String> d = new Vector<String>();
			out.printf("%s = {", Database.lowerCaseString(sig.returnType));
			String[] dom = getDiscreteDomainAsArray(node.node);
			for(int i = 0; i < dom.length; i++) {
				if(i > 0) out.print(", ");
				String elem = Database.upperCaseString(dom[i]); 
				out.print(elem);
				d.add(elem);
			}
			out.println("}");
			domains.put(sig.returnType, d);
		}
		out.println();
		
		// write predicate declarations
		out.println("// predicate declarations");
		for(Signature sig : getSignatures()) {
			String[] argTypes;
			if(sig.returnType.equals("Boolean"))
				argTypes = new String[sig.argTypes.length];
			else {
				argTypes = new String[sig.argTypes.length + 1];
				argTypes[argTypes.length-1] = Database.lowerCaseString(sig.returnType);
			}
			for(int i = 0; i < sig.argTypes.length; i++) {
				if(sig.argTypes[i].length() == 0)
					throw new Exception("Parameter " + i + " of " + sig.functionName + " has empty type: " + sig);
				argTypes[i] = Database.lowerCaseString(sig.argTypes[i]);
			}
			out.printf("%s(%s)\n", Database.lowerCaseString(sig.functionName), RelationalNode.join(", ", argTypes));			
		}
		out.println();
		
		// mutual exclusiveness and exhaustiveness
		out.println("// mutual exclusiveness and exhaustiveness");
		// - non-boolean nodes
		for(RelationalNode node : getRelationalNodes()) {
			if(node.isConstant || node.isAuxiliary)
				continue;
			if(!node.isBoolean()) {
				out.print(Database.lowerCaseString(node.getFunctionName()));
				out.print('(');
				for(int i = 0; i <= node.params.length; i++) {
					if(i > 0) out.print(", ");
					out.printf("a%d", i);
					if(i == node.params.length) out.print('!');
				}
				out.println(")");
			}
		}
		// TODO - add constraints for functional dependencies in relations
		out.println();
		
		// write formulas (and auxiliary predicate definitions for special nodes) 
		int[] order = getTopologicalOrder();
		for(int i = 0; i < order.length; i++) {
			RelationalNode node = getRelationalNode(order[i]);			
			if(node.isConstant || node.isAuxiliary)
				continue;
			CPT2MLNFormulas converter = new CPT2MLNFormulas(node);
			// write auxiliary definitions and formulas required by certain node types
			if(node.aggregator != null && node.parentMode != null) {
				if(node.aggregator.equals("AVG") && node.parentMode.equals("CP")) { // average of conditional probabilities
					// get the relation that is responsible for grounding the free parameters
					RelationalNode rel = node.getFreeParamGroundingParent();
					if(rel == null)
						throw new Exception("Could not determine relevant relational parent");				
					// add predicate declaration for influence factor
					String influenceRelation = String.format("inflfac_%s_%s", node.getFunctionName(), rel.getFunctionName());
					Signature sig = rel.getSignature();
					writer.writePredicateDecl(influenceRelation, sig.argTypes);
					// write mutual exclusiveness and exhaustiveness definition
					writer.writeMutexDecl(influenceRelation, rel.params, node.addParams);
					// add a precondition that must be added to each CPT formula
					converter.addPrecondition(RelationalNode.formatName(influenceRelation, rel.params));
					// write the formula connecting the influence predicate to the regular relation: if the relation does not hold, there is no influence 
					out.println("!" + rel.getCleanName() + " => !" + RelationalNode.formatName(influenceRelation, rel.params) + ".");
				}
				else if(node.aggregator.equals("OR")) { // noisy or
					
				}
			}
			// write conditional probability distribution
			if(node.hasCPT()) {
				out.println("// CPT for " + node.node.getName());
				out.println("// <group>");			
				
				if(compactFormulas) { 
					// convert using decision trees for compactness
					converter.convert(out);
				}
				else {
					// old method: direct conversion
					CPF cpf = node.node.getCPF();
					int[] addr = new int[cpf.getDomainProduct().length];
					walkCPD_MLNformulas(out, cpf, addr, 0, converter.getPrecondition());
				}				

				out.println("// </group>\n");
			}
			else {
				if(node.isNoisyOr()) {
					out.print(writer.formatAsAtom(node.getCleanName()) + " <=> ");
					int k = 0;
					for(RelationalNode parent : node.getParents()) {
						// get the parameters that are free in this parent
						Vector<String> freeparams = new Vector<String>();
						for(String p : node.addParams)
							if(parent.hasParam(p))
								freeparams.add(p);
						if(freeparams.isEmpty())
							continue;
						// print the condition
						if(k++ > 0)
							out.print(" v ");						
						out.print("EXIST " + RelationalNode.join(",", freeparams.toArray(new String[0])) + " " + writer.formatAsAtom(parent.toAtom()));
					}
					if(k == 0)
						throw new Exception("None of the parents of OR-node " + node + " handle any of the free parameters.");
					out.println();
				}					
			}
		}
	}
	
	protected void walkCPD_MLNformulas(PrintStream out, CPF cpf, int[] addr, int i, String precondition) {
		BeliefNode[] nodes = cpf.getDomainProduct();
		if(i == addr.length) { // we have a complete address
			// collect values of constants in order to replace references to them in the individual predicates 
			HashMap<String,String> constantValues = new HashMap<String,String>();
			for(int j = 0; j < addr.length; j++) {
				RelationalNode rn = getRelationalNode(nodes[j]);
				if(rn.isConstant) {
					String value = ((Discrete)rn.node.getDomain()).getName(addr[j]);
					constantValues.put(rn.functionName, value);
				}
			}
			// for each element of the address obtain the corresponding literal/predicate
			StringBuffer sb = new StringBuffer();
			for(int j = 0; j < addr.length; j++) {
				RelationalNode rn = getRelationalNode(nodes[j]);
				if(!rn.isConstant) {
					if(j > 0)
						sb.append(" ^ ");
					sb.append(rn.toLiteral(addr[j], constantValues));
				}
			}
			if(precondition != null) {
				sb.append(" ^ " + precondition);
			}
			// get the weight
			int realAddr = cpf.addr2realaddr(addr);
			double value = ((ValueDouble)cpf.get(realAddr)).getValue();
			double weight = Math.log(value);
			if(Double.isInfinite(weight)) weight = -100.0;
			// print weight and formula
			out.printf("%f %s\n", weight, sb.toString()); 
		}
		else { // the address is yet incomplete -> consider all ways of setting the next e
			Discrete dom = (Discrete)nodes[i].getDomain();
			for(int j = 0; j < dom.getOrder(); j++) {
				addr[i] = j;
				walkCPD_MLNformulas(out, cpf, addr, i+1, precondition);
			}
		}
	}
	
	public ParentGrounder getParentGrounder(RelationalNode node) throws Exception {
		ParentGrounder pg = this.parentGrounders.get(node);
		if(pg == null) {
			pg = new ParentGrounder(this, node);
			parentGrounders.put(node, pg);
		}
		return pg;
	}
	
	public void addRelationKey(RelationKey k) {
		Collection<RelationKey> list = relationKeys.get(k.relation);
		if(list == null) { 
			list = new Vector<RelationKey>();
			relationKeys.put(k.relation.toLowerCase(), list);
		}
		list.add(k);
	}
	
	/**
	 * prepares this network for learning by materializing additional nodes (e.g. for noisy-or)
	 * @throws Exception 
	 */
	public void prepareForLearning() throws Exception {
		for(RelationalNode node : getRelationalNodes().toArray(new RelationalNode[0])) {
			if(node.parentMode != null && node.parentMode.equals("AUX")) { // create an auxiliary node that contains the ungrounded parameters 
				// create fully grounded variant
				String[] params = new String[node.params.length + node.addParams.length];
				int i = 0;
				for(int j = 0; j < node.params.length; j++)
					params[i++] = node.params[j];
				for(int j = 0; j < node.addParams.length; j++)
					params[i++] = node.addParams[j];
				String fullName = RelationalNode.formatName(node.getFunctionName() + "_" + RelationalNode.join("", node.addParams), params);
				BeliefNode fullyGroundedNode = this.addNode(fullName);
				fullyGroundedNode.setDomain(node.node.getDomain());
				// create the corresponding relational node and define a signature for it
				RelationalNode fullyGroundedRelNode = new RelationalNode(this, fullyGroundedNode); 
				addRelationalNode(fullyGroundedRelNode);
				// - determine argument types for signature
				String[] argTypes = new String[params.length];
				Signature origSig = node.getSignature();
				RelationalNode[] relParents = getRelationalParents(node);
				for(int j = 0; j < params.length; j++) {
					if(j < node.params.length)
						argTypes[j] = origSig.argTypes[j];
					else { // check relational parents for parameter match		
						boolean haveType = false;
						for(int k = 0; k < relParents.length && !haveType; k++) {
							Signature sig = relParents[k].getSignature();
							for(int l = 0; l < relParents[k].params.length; l++) {
								if(relParents[k].params[l].equals(params[j])) {
									argTypes[j] = sig.argTypes[l];
									haveType = true;
									break;
								}									
							}
						}
						if(!haveType) 
							throw new Exception("Could not determine type of free parameter " + params[j]);
					}
				}
				// - add signature
				addSignature(fullyGroundedRelNode, new Signature(fullyGroundedRelNode.getFunctionName(), origSig.returnType, argTypes));
				// connect all parents of this node to the fully grounded version
				BeliefNode[] parents = this.bn.getParents(node.node);
				for(BeliefNode parent : parents) {
					this.bn.disconnect(parent, node.node);
					this.bn.connect(parent, fullyGroundedNode);
				}
				// connect the fully grounded version to the original child
				this.bn.connect(fullyGroundedNode, node.node);
				// modify the original child
				node.parentMode = "";
				node.setLabel();
				//node.node.setName(RelationalNode.formatName(node.getFunctionName(), node.params));				
			}
		}
		//show();
	}
}


