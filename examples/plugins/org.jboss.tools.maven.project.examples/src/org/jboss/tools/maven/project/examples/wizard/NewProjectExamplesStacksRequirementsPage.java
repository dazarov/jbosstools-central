/*************************************************************************************
 * Copyright (c) 2012-2014 Red Hat, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     JBoss by Red Hat - Initial implementation.
 ************************************************************************************/
package org.jboss.tools.maven.project.examples.wizard;

import static org.jboss.tools.stacks.core.model.StacksUtil.getArchetype;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jst.j2ee.web.project.facet.WebFacetUtils;
import org.eclipse.m2e.core.internal.MavenPluginActivator;
import org.eclipse.m2e.core.ui.internal.M2EUIPluginActivator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.runtime.RuntimeManager;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IRuntimeLifecycleListener;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.internal.facets.FacetUtil;
import org.jboss.ide.eclipse.as.core.util.RuntimeUtils;
import org.jboss.jdf.stacks.model.ArchetypeVersion;
import org.jboss.jdf.stacks.model.Runtime;
import org.jboss.jdf.stacks.model.Stacks;
import org.jboss.tools.maven.core.MavenCoreActivator;
import org.jboss.tools.maven.project.examples.MavenProjectExamplesActivator;
import org.jboss.tools.maven.project.examples.Messages;
import org.jboss.tools.maven.project.examples.internal.stacks.StacksArchetypeUtil;
import org.jboss.tools.maven.project.examples.utils.MavenArtifactHelper;
import org.jboss.tools.project.examples.fixes.AbstractRuntimeFix;
import org.jboss.tools.project.examples.fixes.IDownloadRuntimeProvider;
import org.jboss.tools.project.examples.fixes.IProjectExamplesFix;
import org.jboss.tools.project.examples.model.ArchetypeModel;
import org.jboss.tools.project.examples.model.ProjectExampleWorkingCopy;
import org.jboss.tools.runtime.core.RuntimeCoreActivator;
import org.jboss.tools.runtime.core.model.DownloadRuntime;
import org.jboss.tools.stacks.core.model.StacksUtil;

public class NewProjectExamplesStacksRequirementsPage extends MavenExamplesRequirementsPage {

	private static final String PAGE_NAME = "org.jboss.tools.project.examples.stacksrequirements"; //$NON-NLS-1$

	private static final String TARGET_RUNTIME = "targetRuntime"; //$NON-NLS-1$

	private Map<ArchetypeVersion, IStatus> enterpriseRepoStatusMap = new HashMap<>();

	private org.jboss.jdf.stacks.model.Archetype stacksArchetype;   

	private ArchetypeVersion version;
	
	private Button useBlankArchetype;

	private Stacks stacks;

	private IRuntimeLifecycleListener listener;

	private Combo serverTargetCombo;

	private Map<String, IRuntime> serverRuntimes;

	private String stacksType;

	private EnterpriseRepoCheckJob job;
	
	private boolean exampleInitialized;

	public NewProjectExamplesStacksRequirementsPage() {
		this(null);
	}
	
	public NewProjectExamplesStacksRequirementsPage(ProjectExampleWorkingCopy projectExample) {
		super(PAGE_NAME, projectExample);
	    fieldsWithHistory = new HashMap<>();
	    stacks = MavenProjectExamplesActivator.getDefault().getCachedStacks();
	    initDialogSettings();
	}

	@Override
	public String getProjectExampleType() {
		return "mavenArchetype"; //$NON-NLS-1$
	}
	
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
	}
	
	@Override
	public void setProjectExample(ProjectExampleWorkingCopy example) {
		exampleInitialized = false;
		if (example != null) {
			projectExample = example;
			super.setProjectExample(projectExample);
			stacksType = projectExample.getStacksType();
			if (stacksType == null) {
				String stacksId = projectExample.getStacksId();
				stacksArchetype = getArchetype(stacksId, stacks);
			}

			setArchetypeVersion();
			
			wizardContext.setProperty(MavenProjectConstants.ENTERPRISE_TARGET, isEnterpriseTargetRuntime());
			
			exampleInitialized = true;
			
			validateEnterpriseRepo();			
		}
	}

	private void setArchetypeVersion() {
		version = null;
		ArchetypeModel mavenArchetype = null;
		StringBuilder description = new StringBuilder();
		
		StacksArchetypeUtil stacksArchetypeUtil = new StacksArchetypeUtil();
		if (useBlankArchetype != null) {
			useBlankArchetype.setVisible(false);
		}
		if (stacksType == null && stacksArchetype == null) {
			description.append(projectExample.getDescription());
		} else {
			
			boolean useBlank = useBlankArchetype != null && !useBlankArchetype.isDisposed() && useBlankArchetype.getSelection();
			IRuntime wtpRuntime = getSelectedRuntime();
	
			org.jboss.jdf.stacks.model.Archetype stArch = null;
			if (stacksType != null) {
				version = stacksArchetypeUtil.getArchetype(stacksType, useBlank, wtpRuntime, stacks);
				if (version != null) {
					stArch = version.getArchetype();
				}
			}
			
			if (stArch == null) {
				String stacksId = projectExample.getStacksId();
				stacksArchetype = getArchetype(stacksId, stacks);
				
				stArch = useBlank && stacksArchetype != null && stacksArchetype.getBlank() != null ?stacksArchetype.getBlank():stacksArchetype;
				if (stArch != null && wtpRuntime != null && wtpRuntime.getRuntimeType() != null) {
					Runtime stacksRuntime = StacksArchetypeUtil.getRuntimeFromWtp(stacks, wtpRuntime);
					if (stacksRuntime != null) {
						List<ArchetypeVersion> compatibleVersions = StacksUtil.getCompatibleArchetypeVersions(stArch, stacksRuntime);
						if (compatibleVersions != null && !compatibleVersions.isEmpty()) {
							version = compatibleVersions.get(0);
						}
					} else {
						//No stacks runtime matching that server id
					}
				}
			}
			
			if (version == null && stArch != null) {
				version = StacksUtil.getDefaultArchetypeVersion(stArch, stacks);
			}
			
			String exampleDescription = projectExample.getDescription();
			if (exampleDescription == null || exampleDescription.trim().isEmpty()) {
				//Fall back on archetype description
				String archetypeDescription = version == null? null : version.getArchetype().getDescription();
				if (archetypeDescription == null || archetypeDescription.trim().isEmpty()) {
					description.append("No description available"); //$NON-NLS-1$
				} else {
					description.append(archetypeDescription);
				}
			} else {
				description.append(exampleDescription);
			}
			
			try {
				if (version != null) {
					mavenArchetype = createArchetypeModel(projectExample.getArchetypeModel(), version);
					boolean hasBlank = stacksArchetypeUtil.hasBlankArchetype(version, wtpRuntime, stacks);
					if (useBlankArchetype != null) {
						useBlankArchetype.setVisible(hasBlank);
					}
					projectExample.setArchetypeModel(mavenArchetype);
				}
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
		}
		
		
		if (mavenArchetype == null) {
			mavenArchetype = projectExample.getArchetypeModel();
		}
		
		wizardContext.setProperty(MavenProjectConstants.ARCHETYPE_MODEL, mavenArchetype);
		if (mavenArchetype != null) {
			description.append("\r\n").append("\r\n") //$NON-NLS-1$ //$NON-NLS-2$
			.append("Project based on the ") //$NON-NLS-1$
			.append(mavenArchetype.getGAV())
			.append(" Maven archetype"); //$NON-NLS-1$
		}
		
		setDescriptionText(description.toString());
	}

	private IRuntime getSelectedRuntime() {
		if (serverTargetCombo != null && !serverTargetCombo.isDisposed()) {
			String wtpServerId = serverTargetCombo.getText();
			return serverRuntimes.get(wtpServerId);
		}
		return null;
	}
	
	private ArchetypeModel createArchetypeModel(ArchetypeModel archetypeModel, ArchetypeVersion archetypeVersion) throws CloneNotSupportedException {
		ArchetypeModel a = (ArchetypeModel) archetypeModel.clone(); 
		a.setArchetypeArtifactId(archetypeVersion.getArchetype().getArtifactId());
		a.setArchetypeGroupId(archetypeVersion.getArchetype().getGroupId());
		a.setArchetypeVersion(archetypeVersion.getVersion());
		a.setArchetypeRepository(archetypeVersion.getRepositoryURL());
		return a;
	}

	@Override
	protected void setSelectionArea(Composite composite) {

		useBlankArchetype = new Button(composite, SWT.CHECK);
		GridData gd = new GridData(SWT.BEGINNING, SWT.FILL, true, false, 2, 1);
		gd.verticalAlignment = SWT.TOP;
		
		useBlankArchetype.setLayoutData(gd);
		useBlankArchetype.setText("Create a blank project"); //$NON-NLS-1$
		useBlankArchetype.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				setArchetypeVersion();
				validateEnterpriseRepo();
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}
		});
		
		listener = new IRuntimeLifecycleListener() {
			
			@Override
			public void runtimeRemoved(IRuntime runtime) {
				runInUIThread();
			}
			
			@Override
			public void runtimeChanged(IRuntime runtime) {
				runInUIThread();
			}
			
			@Override
			public void runtimeAdded(IRuntime runtime) {
				runInUIThread();
			}
			
			private void runInUIThread() {
				Display.getDefault().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						configureRuntimeCombo();
					}
				});
			}
			
		};
		ServerCore.addRuntimeLifecycleListener(listener);

		createServerTargetComposite(composite);

	}
	
	@Override
	public void setVisible(boolean visible) {
		if (visible) {
		  if (useBlankArchetype != null) {
				useBlankArchetype.getParent().layout(true);
		  }
		
          if(!isHistoryLoaded) {
            loadInputHistory();
            isHistoryLoaded = true;
          } else {
            saveInputHistory();
          }
          
		  wizardContext.setProperty(MavenProjectConstants.ENTERPRISE_TARGET, isEnterpriseTargetRuntime());
		}
		
		super.setVisible(visible);
	}
	
	protected void createServerTargetComposite(Composite composite) {
		
		Composite parent = new Composite(composite, SWT.NONE);
		parent.setLayout(new GridLayout(2, false));
		parent.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		
		Label serverTargetLabel = new Label(parent, SWT.NONE);
		serverTargetLabel.setText(Messages.ArchetypeExamplesWizardFirstPage_Target_Runtime_Label);

		GridData gridData = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
		serverTargetCombo = new Combo(parent, SWT.BORDER | SWT.READ_ONLY);
		serverTargetCombo.setLayoutData(gridData);
		serverTargetCombo.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				if (isCurrentPage()) {
					setArchetypeVersion();
					wizardContext.setProperty(MavenProjectConstants.ENTERPRISE_TARGET, isEnterpriseTargetRuntime());
				}
				validateEnterpriseRepo();
			}
		});
		
		configureRuntimeCombo();

	}
	
	public boolean isEnterpriseTargetRuntime() {
		if (serverTargetCombo == null)
			return false;
		//server runtime names are unique so name == server id
		String serverId = serverTargetCombo.getText();
		IRuntime runtime = serverRuntimes.get(serverId);
		return (runtime != null && RuntimeUtils.isEAP(runtime));
	}


	protected void configureRuntimeCombo() {
		if (serverTargetCombo == null || serverTargetCombo.isDisposed()) {
			return;
		}
		int i =0, selectedRuntimeIdx = 0;
		String lastUsedRuntime = dialogSettings.get(TARGET_RUNTIME);

		//TODO read facet version from project example metadata
		serverRuntimes = getServerRuntimes(WebFacetUtils.WEB_30);
		serverTargetCombo.removeAll();
		serverTargetCombo.add(Messages.ArchetypeExamplesWizardFirstPage_No_TargetRuntime);
		for (Map.Entry<String, IRuntime> entry : serverRuntimes.entrySet()) {
			serverTargetCombo.add(entry.getKey());
			++i;
			IRuntime runtime = entry.getValue();
			if (lastUsedRuntime != null && lastUsedRuntime.equals(runtime.getId())) {
				selectedRuntimeIdx = i;
			}
		}
				
		if (selectedRuntimeIdx > 0) {
			serverTargetCombo.select(selectedRuntimeIdx);
		}
	}
	
	protected Map<String, IRuntime> getServerRuntimes(
			IProjectFacetVersion facetVersion) {
		Set<org.eclipse.wst.common.project.facet.core.runtime.IRuntime> runtimesSet;
		if (facetVersion == null) {
			runtimesSet =RuntimeManager.getRuntimes();
		} else {
			runtimesSet = RuntimeManager.getRuntimes(Collections.singleton(facetVersion));
		}
		
		Map<String, IRuntime> runtimesMap = new LinkedHashMap<>();
		for (org.eclipse.wst.common.project.facet.core.runtime.IRuntime r : runtimesSet) {
			IRuntime serverRuntime = FacetUtil.getRuntime(r);
			if (serverRuntime != null) {
				runtimesMap.put(r.getLocalizedName(), serverRuntime);
			}
		}
		return runtimesMap;
	}

	@Override
	protected void validateEnterpriseRepo() {
		stopMavenRepoCheckJob();
		if (exampleInitialized && warningComponent != null) {
			warningComponent.setLinkText(""); //$NON-NLS-1$
			
			final Set<String> reqDeps = StacksArchetypeUtil.getRequiredDependencies(version);
			
			if (reqDeps == null || reqDeps.isEmpty()) {
				return;
			}
			if (!isEnterpriseTargetRuntime() && Boolean.TRUE.equals(wizardContext.getProperty(MavenProjectConstants.HAS_ENTERPRISE_PROPERTY))) {
				return;
			}

			warningComponent.setLinkText(Messages.NewProjectExamplesStacksRequirementsPage_Checking_Enterprise_Maven_Repo_Availability, false); 
			IStatus enterpriseRepoStatus = enterpriseRepoStatusMap.get(version);
			if (enterpriseRepoStatus != null) {
				updateWarningComponent(enterpriseRepoStatus);
				return;
			}
			
			job = new EnterpriseRepoCheckJob();
			
			job.addJobChangeListener(new JobChangeAdapter() {

				public void done(IJobChangeEvent event) {
					if (job.isCancelled()) {
						return;
					}
					enterpriseRepoStatusMap.put(version, job.getCheckResult());
					final IStatus status = event.getResult().isOK()?job.getCheckResult():event.getResult();
					Display.getDefault().syncExec( new Runnable() {  public void run() {
						updateWarningComponent(status);
					} });
				};
			});
			
			job.schedule();
		}
	}

	private void stopMavenRepoCheckJob() {
		if (job != null) {
			job.cancel();
		}		
	}

	private void updateWarningComponent(IStatus status) {
		if (status == null || status.isOK()) {
			warningComponent.setRepositoryUrls(null);
			warningComponent.setLinkText(""); //$NON-NLS-1$
		} else {
			warningComponent.setRepositoryUrls(StacksArchetypeUtil.getAdditionalRepositories(version));
			warningComponent.setLinkText(status.getMessage());
		}
		wizardContext.setProperty(MavenProjectConstants.ENTERPRISE_REPO_STATUS, status);
	}
	
	
	@Override
	public void dispose() {
		if (dialogSettings != null && serverRuntimes != null && serverTargetCombo != null) {
			IRuntime lastUsedRuntime = serverRuntimes.get(serverTargetCombo.getText());
			if (lastUsedRuntime != null) {
				dialogSettings.put(TARGET_RUNTIME, lastUsedRuntime.getId());
			}
		}
		if (listener != null) {
			ServerCore.removeRuntimeLifecycleListener(listener);
			listener = null;
		}
		stopMavenRepoCheckJob();
		job = null;
		enterpriseRepoStatusMap.clear();
		MavenCoreActivator.getDefault().unregisterMavenSettingsChangeListener(this);
	    saveInputHistory();

		super.dispose();
	}
	
	
	/********** code below copied from {@link AbstractMavenWizardPage} **********/
	
	/** the history limit */
	protected static final int MAX_HISTORY = 15;

	/** dialog settings to store input history */
	protected IDialogSettings dialogSettings;

    /** the Map of field ids to List of comboboxes that share the same history */
    private Map<String, List<Combo>> fieldsWithHistory;

    private boolean isHistoryLoaded = false;


	  /** Loads the dialog settings using the page name as a section name. */
	  private void initDialogSettings() {
	    IDialogSettings pluginSettings;
	    
	    // This is strictly to get SWT Designer working locally without blowing up.
	    if( MavenPluginActivator.getDefault() == null ) {
	      pluginSettings = new DialogSettings("Workbench"); //$NON-NLS-1$
	    }
	    else {
	      pluginSettings = M2EUIPluginActivator.getDefault().getDialogSettings();      
	    }
	    
	    dialogSettings = pluginSettings.getSection(getName());
	    if(dialogSettings == null) {
	      dialogSettings = pluginSettings.addNewSection(getName());
	      pluginSettings.addSection(dialogSettings);
	    }
	  }

	  /** Loads the input history from the dialog settings. */
	  private void loadInputHistory() {
	    for(Map.Entry<String, List<Combo>> e : fieldsWithHistory.entrySet()) {
	      String id = e.getKey();
	      String[] items = dialogSettings.getArray(id);
	      if(items != null) {
	        for(Combo combo : e.getValue()) {
	          String text = combo.getText();
	          combo.setItems(items);
	          if(text.length() > 0) {
	            // setItems() clears the text input, so we need to restore it
	            combo.setText(text);
	          }
	        }
	      }
	    }
	  }

	  /** Saves the input history into the dialog settings. */
	  private void saveInputHistory() {
	    for(Map.Entry<String, List<Combo>> e : fieldsWithHistory.entrySet()) {
	      String id = e.getKey();

	      Set<String> history = new LinkedHashSet<>(MAX_HISTORY);

	      for(Combo combo : e.getValue()) {
	        String lastValue = combo.getText();
	        if(lastValue != null && lastValue.trim().length() > 0) {
	          history.add(lastValue);
	        }
	      }

	      Combo combo = e.getValue().iterator().next();
	      String[] items = combo.getItems();
	      for(int j = 0; j < items.length && history.size() < MAX_HISTORY; j++ ) {
	        history.add(items[j]);
	      }

	      dialogSettings.put(id, history.toArray(new String[history.size()]));
	    }
	  }

	  /** Adds an input control to the list of fields to save. */
	  protected void addFieldWithHistory(String id, Combo combo) {
	    if(combo != null) {
	      List<Combo> combos = fieldsWithHistory.get(id);
	      if(combos == null) {
	        combos = new ArrayList<>();
	        fieldsWithHistory.put(id, combos);
	      }
	      combos.add(combo);
	    }
	  }

	@Override
	public void onSettingsChanged() {
		resetRepoValidation();
	}

	@Override
	public void onWizardContextChange(String key, Object value) {
		if (MavenProjectConstants.HAS_ENTERPRISE_PROPERTY.equals(key)) {
			resetRepoValidation();
		} else {
			super.onWizardContextChange(key, value);
		}
	}

	private void resetRepoValidation() {
        Display.getDefault().asyncExec( new Runnable() {  public void run() { 
			//Reset previous status
			enterpriseRepoStatusMap.clear();
			validateEnterpriseRepo();
        }});
	}

	public class EnterpriseRepoCheckJob extends Job {

		private boolean cancelled;

		private IStatus checkResult;

		public EnterpriseRepoCheckJob() {
			super(Messages.NewProjectExamplesStacksRequirementsPage_Check_Maven_Repo_Job);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			checkResult = MavenArtifactHelper.checkRequirementsAvailable(version);
			return Status.OK_STATUS;
		}

		@Override
		protected void canceling() {
			cancelled = true;
			super.canceling();
		}

		boolean isCancelled() {
			return cancelled;
		}

		IStatus getCheckResult() {
			return checkResult;
		}
	}

	
	@Override
	protected IProjectExamplesFix getSelectedProjectFix() {
		IProjectExamplesFix fix = super.getSelectedProjectFix();
		if (fix instanceof AbstractRuntimeFix) {
			return new StacksBasedRuntimeFix((AbstractRuntimeFix)fix);
		}
		return fix;
	}
	
	private class StacksBasedRuntimeFix implements IProjectExamplesFix, IDownloadRuntimeProvider {
		
		private AbstractRuntimeFix delegate;

		StacksBasedRuntimeFix(AbstractRuntimeFix delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean isSatisfied() {
			return delegate.isSatisfied();
		}

		@Override
		public boolean fix(IProgressMonitor monitor) {
			return delegate.fix(monitor);
		}

		@Override
		public Collection<DownloadRuntime> getDownloadRuntimes(IProgressMonitor monitor) {
			List<Runtime> stacksRuntimes = StacksUtil.getCompatibleServerRuntimes(stacksArchetype, stacks);
			if (stacksRuntimes != null && !stacksRuntimes.isEmpty()) {
				List<DownloadRuntime> downloadableRuntimes = new ArrayList<>(stacksRuntimes.size());
				for (Runtime r : stacksRuntimes) {
					DownloadRuntime dr = RuntimeCoreActivator.getDefault().findDownloadRuntime(r.getId(), monitor);
					if (dr == null){
						String downloadUrl = StacksUtil.isEnterprise(r)?null:r.getUrl();
						dr = new DownloadRuntime(r.getId(),
								r.getName(), 
								r.getVersion(), 
								downloadUrl);
						dr.setDisclaimer(!StacksUtil.isEnterprise(r));
						dr.setHumanUrl(r.getUrl());
                    } 
                    downloadableRuntimes.add(dr);
				}
				return downloadableRuntimes;
			}
			return delegate.getDownloadRuntimes(monitor);
		}

		@Override
		public String getType() {
			return delegate.getType();
		}

		@Override
		public String getDescription() {
			return delegate.getDescription();
		}

		@Override
		public String getLabel() {
			return delegate.getLabel();
		}

		@Override
		public boolean isRequired() {
			return delegate.isRequired();
		}
	}
	
}
