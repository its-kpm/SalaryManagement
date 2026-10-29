/** CSV preview/application and formula-safe employee salary export module. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"employee", "compensation", "reference", "audit"})
package com.acme.salary.transfer;
