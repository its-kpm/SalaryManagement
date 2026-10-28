/** Reporting module boundary; business functionality arrives in later commits. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"employee", "compensation", "reference"})
package com.acme.salary.reporting;
