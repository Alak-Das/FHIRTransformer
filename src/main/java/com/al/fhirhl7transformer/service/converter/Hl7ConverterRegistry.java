package com.al.fhirhl7transformer.service.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Hl7ConverterRegistry {

    private final PatientConverter patientConverter;
    private final EncounterConverter encounterConverter;
    private final ObservationConverter observationConverter;
    private final AllergyConverter allergyConverter;
    private final ConditionConverter conditionConverter;
    private final MedicationConverter medicationConverter;
    private final ProcedureConverter procedureConverter;
    private final InsuranceConverter insuranceConverter;
    private final AppointmentConverter appointmentConverter;
    private final ImmunizationConverter immunizationConverter;
    private final ServiceRequestConverter serviceRequestConverter;
    private final DiagnosticReportConverter diagnosticReportConverter;
    private final MedicationAdministrationConverter medicationAdministrationConverter;
    private final PractitionerConverter practitionerConverter;
    private final LocationConverter locationConverter;
    private final OrganizationConverter organizationConverter;
    private final SpecimenConverter specimenConverter;
    private final CommunicationConverter communicationConverter;
    private final DeviceConverter deviceConverter;
    private final OrderConverter orderConverter;
    private final DocumentReferenceConverter documentReferenceConverter;
    private final CarePlanConverter carePlanConverter;
    private final PractitionerRoleConverter practitionerRoleConverter;
    private final MessageHeaderConverter messageHeaderConverter;

    @Autowired
    public Hl7ConverterRegistry(
            PatientConverter patientConverter,
            EncounterConverter encounterConverter,
            ObservationConverter observationConverter,
            AllergyConverter allergyConverter,
            ConditionConverter conditionConverter,
            MedicationConverter medicationConverter,
            ProcedureConverter procedureConverter,
            InsuranceConverter insuranceConverter,
            AppointmentConverter appointmentConverter,
            ImmunizationConverter immunizationConverter,
            ServiceRequestConverter serviceRequestConverter,
            DiagnosticReportConverter diagnosticReportConverter,
            MedicationAdministrationConverter medicationAdministrationConverter,
            PractitionerConverter practitionerConverter,
            LocationConverter locationConverter,
            OrganizationConverter organizationConverter,
            SpecimenConverter specimenConverter,
            CommunicationConverter communicationConverter,
            DeviceConverter deviceConverter,
            OrderConverter orderConverter,
            DocumentReferenceConverter documentReferenceConverter,
            CarePlanConverter carePlanConverter,
            PractitionerRoleConverter practitionerRoleConverter,
            MessageHeaderConverter messageHeaderConverter) {
        this.patientConverter = patientConverter;
        this.encounterConverter = encounterConverter;
        this.observationConverter = observationConverter;
        this.allergyConverter = allergyConverter;
        this.conditionConverter = conditionConverter;
        this.medicationConverter = medicationConverter;
        this.procedureConverter = procedureConverter;
        this.insuranceConverter = insuranceConverter;
        this.appointmentConverter = appointmentConverter;
        this.immunizationConverter = immunizationConverter;
        this.serviceRequestConverter = serviceRequestConverter;
        this.diagnosticReportConverter = diagnosticReportConverter;
        this.medicationAdministrationConverter = medicationAdministrationConverter;
        this.practitionerConverter = practitionerConverter;
        this.locationConverter = locationConverter;
        this.organizationConverter = organizationConverter;
        this.specimenConverter = specimenConverter;
        this.communicationConverter = communicationConverter;
        this.deviceConverter = deviceConverter;
        this.orderConverter = orderConverter;
        this.documentReferenceConverter = documentReferenceConverter;
        this.carePlanConverter = carePlanConverter;
        this.practitionerRoleConverter = practitionerRoleConverter;
        this.messageHeaderConverter = messageHeaderConverter;
    }

    public PatientConverter getPatientConverter() {
        return patientConverter;
    }

    public EncounterConverter getEncounterConverter() {
        return encounterConverter;
    }

    public ObservationConverter getObservationConverter() {
        return observationConverter;
    }

    public AllergyConverter getAllergyConverter() {
        return allergyConverter;
    }

    public ConditionConverter getConditionConverter() {
        return conditionConverter;
    }

    public MedicationConverter getMedicationConverter() {
        return medicationConverter;
    }

    public ProcedureConverter getProcedureConverter() {
        return procedureConverter;
    }

    public InsuranceConverter getInsuranceConverter() {
        return insuranceConverter;
    }

    public AppointmentConverter getAppointmentConverter() {
        return appointmentConverter;
    }

    public ImmunizationConverter getImmunizationConverter() {
        return immunizationConverter;
    }

    public ServiceRequestConverter getServiceRequestConverter() {
        return serviceRequestConverter;
    }

    public DiagnosticReportConverter getDiagnosticReportConverter() {
        return diagnosticReportConverter;
    }

    public MedicationAdministrationConverter getMedicationAdministrationConverter() {
        return medicationAdministrationConverter;
    }

    public PractitionerConverter getPractitionerConverter() {
        return practitionerConverter;
    }

    public LocationConverter getLocationConverter() {
        return locationConverter;
    }

    public OrganizationConverter getOrganizationConverter() {
        return organizationConverter;
    }

    public SpecimenConverter getSpecimenConverter() {
        return specimenConverter;
    }

    public CommunicationConverter getCommunicationConverter() {
        return communicationConverter;
    }

    public DeviceConverter getDeviceConverter() {
        return deviceConverter;
    }

    public OrderConverter getOrderConverter() {
        return orderConverter;
    }

    public DocumentReferenceConverter getDocumentReferenceConverter() {
        return documentReferenceConverter;
    }

    public CarePlanConverter getCarePlanConverter() {
        return carePlanConverter;
    }

    public PractitionerRoleConverter getPractitionerRoleConverter() {
        return practitionerRoleConverter;
    }

    public MessageHeaderConverter getMessageHeaderConverter() {
        return messageHeaderConverter;
    }
}
