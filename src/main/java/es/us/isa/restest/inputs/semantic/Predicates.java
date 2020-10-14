package es.us.isa.restest.inputs.semantic;

import es.us.isa.restest.specification.OpenAPISpecification;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.*;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;


import java.nio.file.Path;
import java.util.*;
import es.us.isa.restest.configuration.pojos.TestParameter;

import static es.us.isa.restest.inputs.semantic.NLPUtils.posTagging;

public class Predicates {
    // TODO: Consider the possibility of adding owl predicates
    // TODO: Add support/threshold
    // TODO: Add by combination of length/support
    // TODO: Add limit
    // TODO: Wordnet/Description in case the function returns no results
    // TODO: size()=0 exception
    public static Map<TestParameter, List<String>> getPredicates(SemanticOperation semanticOperation, OpenAPISpecification spec){
        Set<TestParameter> parameters = semanticOperation.getSemanticParameters().keySet();

        Map<TestParameter, List<String>> res = new HashMap<>();

        for(TestParameter p: parameters){
            String parameterName = p.getName();

            // If the paramater name is only a character, compare with description
            if(parameterName.length() == 1){
                // TODO: NullPointer
                PathItem pathItem = spec.getSpecification().getPaths().get(semanticOperation.getOperationPath());
                String description = getParameterDescription(pathItem, p.getName());
                parameterName =  posTagging(description, p.getName()).get(0);
            }

            List<String> predicates = getPredicatesOfSingleParameter(parameterName);
            res.put(p, predicates);
        }
        return res;
    }


    public static List<String> getPredicatesOfSingleParameter(String parameterName){

        // Query creation
        String queryString = generatePredicateQuery(parameterName);

        // Query execution
        List<String> res = executePredicateSPARQLQuery(queryString);

        if(res.size() < 5){
            String[] words = parameterName.split("_");
            // If snake_case
            if(words.length > 1){
                // Join words
                res.addAll(executePredicateSPARQLQuery(String.join("", words)));

                if(res.size() <5) {
                    // Execute one query for each word in snake_case
                    for(String word: words){
                        res.addAll(executePredicateSPARQLQuery(word));
                    }
                }

            }

            // If camelCase
            String[] wordsCamel = parameterName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");
            if(res.size() < 5 && wordsCamel.length >1){
                // Execute one query for each word in camelCase
                for(String word: wordsCamel){
                    res.addAll(executePredicateSPARQLQuery(word));
                }

            }
        }

        System.out.println(res);
        return res;
    }


    public static String generatePredicateQuery(String parameterName){

        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                "\n" +
                "SELECT distinct ?predicate where {\n" +
                "    ?predicate a rdf:Property\n" +
                "    OPTIONAL { ?predicate rdfs:label ?label }\n" +
                "\n" +
                "FILTER regex(str(?predicate), \"" + parameterName +  "\" , 'i')\n" +
                "}\n" +
                "order by strlen(str(?predicate)) " +
                "\n";

        return queryString;
    }


    public static List<String> executePredicateSPARQLQuery(String queryString){
        List<String> res = new ArrayList<>();

        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.sparqlService("http://dbpedia.org/sparql", query);
        ((QueryEngineHTTP)qexec).addParam("timeout", "10000");

        // Execute query
        int iCount = 0;
        ResultSet rs = qexec.execSelect();
        while (rs.hasNext() && iCount<5) {
            iCount++;

            QuerySolution qs = rs.next();
            Iterator<String> itVars = qs.varNames();

            while(itVars.hasNext()){
                String sVar =itVars.next();
                String szVal = qs.get(sVar).toString();

                res.add(szVal);
            }

        }
        return res;
    }

    private static String getParameterDescription(PathItem pathItem, String parameterName){

        Operation operation = null;

        switch(parameterName) {
            case "get":
                operation = pathItem.getGet();
                break;
            case "put":
                operation = pathItem.getPut();
                break;
            case "post":
                operation = pathItem.getPost();
                break;
            case "delete":
                operation = pathItem.getDelete();
                break;
            case "options":
                operation = pathItem.getOptions();
                break;
            case "head":
                operation = pathItem.getHead();
                break;
            case "patch":
                operation = pathItem.getPatch();
                break;
            case "trace":
                operation = pathItem.getTrace();
                break;
        }

        List<Parameter> parameters = operation.getParameters();

        for(Parameter parameter: parameters){
            if(parameter.getName().equals(parameterName)){
                return  parameter.getDescription();
            }
        }

        return parameterName;
    }

}
