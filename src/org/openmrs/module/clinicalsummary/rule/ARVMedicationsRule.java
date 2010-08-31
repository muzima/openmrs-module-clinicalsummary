/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.clinicalsummary.rule;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.logic.LogicContext;
import org.openmrs.logic.LogicCriteria;
import org.openmrs.logic.LogicException;
import org.openmrs.logic.Rule;
import org.openmrs.logic.result.Result;
import org.openmrs.logic.result.Result.Datatype;
import org.openmrs.logic.rule.RuleParameterInfo;
import org.openmrs.module.clinicalsummary.SummaryService;
import org.openmrs.module.clinicalsummary.cache.SummaryDataSource;
import org.openmrs.module.clinicalsummary.concept.ConceptRegistry;
import org.openmrs.module.clinicalsummary.concept.StandardConceptConstants;
import org.openmrs.module.clinicalsummary.encounter.TypeConstants;
import org.openmrs.util.OpenmrsUtil;

/**
 *
 */
public class ARVMedicationsRule implements Rule {
	
	private static final Log log = LogFactory.getLog(ARVMedicationsRule.class);
	
	/**
	 * @see org.openmrs.logic.Rule#eval(org.openmrs.logic.LogicContext, org.openmrs.Patient,
	 *      java.util.Map)
	 */
	public Result eval(LogicContext context, Patient patient, Map<String, Object> parameters) throws LogicException {
		
		Result result = new Result();
		
		SummaryService service = Context.getService(SummaryService.class);
		
		List<String> typeNames = Arrays.asList(TypeConstants.ADULT_INITIAL,
		    TypeConstants.ADULT_RETURN, TypeConstants.ADULT_NONCLINICAL,
		    TypeConstants.PEDS_INITIAL, TypeConstants.PEDS_RETURN,
		    TypeConstants.PEDS_NONCLINICAL);
		LogicCriteria encounterCriteria = service.parseToken(SummaryDataSource.ENCOUNTER_TYPE).in(typeNames).last();
		LogicCriteria startedCriteria = service.parseToken(SummaryDataSource.CONCEPT).equalTo(StandardConceptConstants.ANTIRETROVIRALS_STARTED);
		
		Result startedResults = context.read(patient, service.getLogicDataSource("summary"), startedCriteria.and(encounterCriteria));
		
		if (log.isDebugEnabled())
			log.debug("Started arv observations for patient: " + patient.getPatientId() + " is: " + startedResults);
		
		if (!startedResults.isEmpty())
			result.addAll(startedResults);
		else {
			
			Concept continueConcept = ConceptRegistry.getCachedConcept(StandardConceptConstants.CONTINUE_REGIMEN);
			Concept refilledConcept = ConceptRegistry.getCachedConcept(StandardConceptConstants.REFILLED);
			Concept dosingConcept = ConceptRegistry.getCachedConcept(StandardConceptConstants.DOSING_CHANGE);
			
			LogicCriteria plannedCriteria = service.parseToken(SummaryDataSource.CONCEPT).equalTo(StandardConceptConstants.ANTIRETROVIRAL_PLAN);
			Result plannedResults = context.read(patient, service.getLogicDataSource("summary"), plannedCriteria.and(encounterCriteria));
			
			if (log.isDebugEnabled())
				log.debug("Planned arv observations for patient: " + patient.getPatientId() + " is: " + plannedResults);
			
			boolean foundPlan = false;
			int planCounter = 0;
			while (planCounter < plannedResults.size() && !foundPlan) {
				Concept valueCoded = plannedResults.toConcept();
				if (OpenmrsUtil.nullSafeEquals(continueConcept, valueCoded)
				        || OpenmrsUtil.nullSafeEquals(refilledConcept, valueCoded)
				        || OpenmrsUtil.nullSafeEquals(dosingConcept, valueCoded))
					foundPlan = true;
				planCounter++;
			}
			
			if (foundPlan) {
				Concept noneConcept = ConceptRegistry.getCachedConcept(StandardConceptConstants.NONE);
				
				LogicCriteria reportedCriteria = service.parseToken(SummaryDataSource.CONCEPT).equalTo(StandardConceptConstants.CURRENT_ANTIRETROVIRAL);
				Result reportedResults = context.read(patient, service.getLogicDataSource("summary"), reportedCriteria.and(encounterCriteria));
				
				if (log.isDebugEnabled())
					log.debug("Reported arv observations for patient: " + patient.getPatientId() + " is: " + reportedResults);
				
				Result reportedResult = new Result();
				
				boolean foundNone = false;
				int reportedCounter = 0;
				while (reportedCounter < reportedResults.size() && !foundNone) {
					Result resultReported = reportedResults.get(reportedCounter);
					reportedResult.add(resultReported);
					Concept valueCoded = resultReported.toConcept();
					if (OpenmrsUtil.nullSafeEquals(noneConcept, valueCoded))
						foundNone = true;
					reportedCounter++;
				}
				
				if (!foundNone)
					result.addAll(reportedResult);
			}
		}
		
		Concept antiConcept = ConceptRegistry.getCachedConcept(StandardConceptConstants.ANTIRETROVIRAL_DRUGS);
		
		LogicCriteria addedCriteria = service.parseToken(SummaryDataSource.CONCEPT).equalTo(StandardConceptConstants.MEDICATION_ADDED);
		Result addedResults = context.read(patient, service.getLogicDataSource("summary"), addedCriteria.and(encounterCriteria));
		
		if (log.isDebugEnabled())
			log.debug("Added arv observations for patient: " + patient.getPatientId() + " is: " + addedResults);
		
		for (Result addedResult : addedResults) {
			Concept valueCoded = addedResult.toConcept();
			if (ConceptRegistry.isCachedParentOf(antiConcept, valueCoded))
				result.add(addedResult);
		}
		
		return result;
	}
	
	/**
	 * @see org.openmrs.logic.Rule#getDefaultDatatype()
	 */
	public Datatype getDefaultDatatype() {
		return Datatype.CODED;
	}
	
	/**
	 * @see org.openmrs.logic.Rule#getDependencies()
	 */
	public String[] getDependencies() {
		return null;
	}
	
	/**
	 * @see org.openmrs.logic.Rule#getParameterList()
	 */
	public Set<RuleParameterInfo> getParameterList() {
		return null;
	}
	
	/**
	 * @see org.openmrs.logic.Rule#getTTL()
	 */
	public int getTTL() {
		return 0;
	}
}
