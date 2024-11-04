package org.isf.stat.gui.report;

import org.isf.medicalinventory.model.MedicalInventory;
import org.isf.menu.manager.Context;
import org.isf.stat.dto.JasperReportResultDto;
import org.isf.stat.manager.JasperReportsManager;
import org.isf.utils.jobjects.MessageDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericReportPharmaceuticalInventory extends DisplayReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(GenericReportPharmaceuticalInventory.class);
	private JasperReportsManager jasperReportsManager = Context.getApplicationContext().getBean(JasperReportsManager.class);

	public GenericReportPharmaceuticalInventory(MedicalInventory medicalInventory, String jasperFileName, int printQtyReal) {
		try {
			JasperReportResultDto jasperReportResultDto = jasperReportsManager.getInventoryReportPdf(medicalInventory, jasperFileName, printQtyReal);
			showReport(jasperReportResultDto);
		} catch (Exception e) {
			LOGGER.error("", e);
			MessageDialog.error(null, "angal.inventory.printing.error.msg");
		}
	}
}
