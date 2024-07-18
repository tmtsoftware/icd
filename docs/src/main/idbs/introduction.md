# Introduction

The Interface Database System (IDBS) was created to help component builders and system engineering understand the programming interfaces of software components created with the TIO Common Software.

## Background

The project was started for the following purposes:

1. To document the interfaces of components based on TIO Common Software in the TIO Software System.
2. To support a more agile development process by tracking changes to component interfaces over the course of TMT construction.
3. To understand how events are used in the software system.
4. To support the Systems Engineering change control process by understanding how planned interface changes influence the software system.
5. To understand the software interfaces between subsystems and components in the software system.
6. To decrease the workload of developers by generating API and ICD documentation that can be used for reviews.
7. Provide a platform for understanding and modeling the interactions of components during observing. This might include adding additional information to the models.

## Design Background

There might be several ways to solve the problems IDBS is addressing. One approach might be to use/extend a source code documentation markup tool such as Doxygen or the 15+ other products a Google search on software documentation shows. The reason this approach wasn’t taken is that the problem is not about documenting source code, it is modeling components and interfaces and delivering a database that provides a basis for understanding and tracking interfaces and their changes. The models need to drive the source, not the other way around. Documentation tools can generate documents but extracting information and putting it into a database is not the scope of these tools.  The primary need is to have a database of interfaces and delivering interface documentation is a second, although important, side effect. Ultimately, it is a question of how to best describe a component and its interfaces.

