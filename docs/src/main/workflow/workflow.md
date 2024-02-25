# IDBS Workflow and Creating ICDs

This section describes how the IDBS fits into the software development process at TMT. This workflow is shown in the context of the TMT Software Quality Assurance Plan and Software Development Process (SQUAP and SDP) [RD01], which describes the entire software development process.

OMOA components consist of HCDs, Assemblies, Sequencers, and Applications. The components are developed incrementally and independently of other subsystem’s components.

Each component has a *public interface* or *application programming interface* (API). The API is based on the services provided by common software. The API shows all the available functionality of a component and subsystem.

Each TIO software ICD consists of a *framework* part and a *detailed* part.  The framework part is written in Word according to the TIO Systems Engineering template. The detailed part is generated from the ICD-DB and can change more frequently without requiring updates to the framework document - at least in the case where the framework information does not change.

A component developer creates a set of model files for each component that describes the component and its interfaces (see the section on @ref[model files](../modelFiles/modelFiles.md)). These model files are text files. When the component interface changes, the developer updates the model files, validates them, and when ready, checks them into a dedicated repository for model files at the GitHub site: (https://github.com/tmt-icd).

When working on or changing an interface used by another TIO subsystem that will be part of an ICD, the component developer should work with the other subsystem developers to determine the correct functionality and API.

Once the model files are checked in, TIO Systems Engineering determines when the new version of a subsystem’s API can be published and given a new incremental version number. Systems Engineering will review any changes that impact other subsystems and ensure they are aware of and agree with changes that will be part of an ICD. New releases of the API documents can be published and will be seen as published in the ICD-DB web user interface.

TIO Systems Engineering also determines when a new ICD can be generated based on the APIs of the constituent subsystems and manages the publication of a new detailed ICD release. When published, the new ICD is also shown as published in the ICD-DB web user interface.

@@@ note
It may be necessary to click the browser refresh button to see a newly published API or ICD that is not yet in the local icd database (The icd web app then automatically ingests any newly published APIs and ICDs into the local database). The command `icd-git –ingest` will also update the local database from the released versions on GitHub.
@@@
