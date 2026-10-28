/** Compensation module boundary; business functionality arrives in later commits. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"employee", "reference", "audit"})
package com.acme.salary.compensation;
