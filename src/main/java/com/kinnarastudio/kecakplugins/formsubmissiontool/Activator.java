package com.kinnarastudio.kecakplugins.formsubmissiontool;

import java.util.ArrayList;
import java.util.Collection;

import com.kinnarastudio.kecakplugins.formsubmissiontool.process.tool.DataListFormSubmissionTool;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
//        registrationList.add(context.registerService(WorkflowFormLoadBinder.class.getName(), new WorkflowFormLoadBinder(), null));
        registrationList.add(context.registerService(FormSubmissionTool.class.getName(), new FormSubmissionTool(), null));
        registrationList.add(context.registerService(com.kinnarastudio.kecakplugins.formsubmissiontool.process.tool.FormSubmissionTool.class.getName(), new com.kinnarastudio.kecakplugins.formsubmissiontool.process.tool.FormSubmissionTool(), null));
        registrationList.add(context.registerService(DataListFormSubmissionTool.class.getName(), new DataListFormSubmissionTool(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}