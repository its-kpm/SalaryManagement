/** Effective-dated salary history and transactional salary-change module. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"employee", "reference", "audit"})
package com.acme.salary.compensation;
