package es.us.isa.restest.generators;

import java.util.Random;

import es.us.isa.restest.configuration.pojos.TestConfigurationObject;
import es.us.isa.restest.configuration.pojos.TestParameter;
import es.us.isa.restest.inputs.ITestDataGenerator;
import es.us.isa.restest.specification.OpenAPISpecification;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.AuthManager;
import es.us.isa.restest.util.IDGenerator;
import es.us.isa.restest.util.SpecificationVisitor;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.parameters.Parameter;

import static es.us.isa.restest.mutation.TestCaseMutation.makeTestCaseFaulty;

public class RandomTestCaseGenerator extends AbstractTestCaseGenerator {

	private long seed = -1;								// Seed
	Random rand;
	
	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, int nTests) {
		this.spec = spec;
		this.conf = conf;
		this.numberOfTest = nTests;
		this.index =0;
		
		this.rand = new Random();
		this.seed = rand.nextLong();
		rand.setSeed(this.seed);
	}

	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, String apiKeysPath, int nTests) {
		this(spec, conf, nTests);
		this.authManager = new AuthManager(apiKeysPath);
	}

	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, Boolean enableFaulty, int nTests) {
		this(spec, conf, nTests);
		this.enableFaulty = enableFaulty;
	}

	public RandomTestCaseGenerator(OpenAPISpecification spec, TestConfigurationObject conf, Boolean enableFaulty, String apiKeysPath, int nTests) {
		this(spec, conf, nTests);
		this.authManager = new AuthManager(apiKeysPath);
		this.enableFaulty = enableFaulty;
	}
	

	// Generate the next test case and update the generation index
	protected TestCase generateNextTestCase(Operation specOperation, es.us.isa.restest.configuration.pojos.Operation testOperation, Boolean faulty, String path, HttpMethod method) {
		
		String testId = removeNotAlfanumericCharacters(testOperation.getOperationId()) + "Test_" + IDGenerator.generateId();
		TestCase test = new TestCase(testId,faulty,testOperation.getOperationId(), path, method);
		
		// Set parameters
		for(TestParameter confParam: testOperation.getTestParameters()) {
			Parameter specParameter = SpecificationVisitor.findParameter(specOperation, confParam.getName());
			
			if (specParameter.getRequired() || rand.nextFloat()<=confParam.getWeight()) {
				ITestDataGenerator generator = generators.get(confParam.getName());
				switch (specParameter.getIn()) {
				case "header":
					test.addHeaderParameter(confParam.getName(), generator.nextValueAsString());
					break;
				case "query":
					test.addQueryParameter(confParam.getName(), generator.nextValueAsString());
					break;
				case "path":
					test.addPathParameter(confParam.getName(), generator.nextValueAsString());
					break;
				case "body":
					test.setBodyParameter(generator.nextValueAsString());
					break;
				default:
					throw new IllegalArgumentException("Parameter type not supported: " + specParameter.getIn());
				}
			}
		}

		if (faulty) 										// If this test case must be faulty, mutate it after it is constructed
			if(!makeTestCaseFaulty(test, specOperation)) 	// If the test case wasn't mutated
				test.setFaulty(false); 						// Set faulty to false, in order to have the right oracle
		
		index++;
		
		return test;
	}
	
	// Returns true if there are more test cases to be generated
	protected boolean hasNext() {
		Boolean res = index<numberOfTest;
		if (index == numberOfTest)
			index = 0;
		return res;
	}

	private long getSeed() {
		return this.seed;
	}

	private void setSeed(long seed) {
		this.seed = seed;
		rand.setSeed(seed);
	}

	private String removeNotAlfanumericCharacters(String s) {
		return s.replaceAll("[^A-Za-z0-9]", "");
	}
}
