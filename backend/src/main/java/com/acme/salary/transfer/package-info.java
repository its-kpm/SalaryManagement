/** Transfer module boundary; business functionality arrives in later commits. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"employee", "compensation", "reference", "audit"})
package com.acme.salary.transfer;
