/*
 * Open Hospital (www.open-hospital.org)
 * Copyright © 2006-2024 Informatici Senza Frontiere (info@informaticisenzafrontiere.org)
 *
 * Open Hospital is a free and open source software for healthcare data management.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * https://www.gnu.org/licenses/gpl-3.0-standalone.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.isf.medicalinventory.gui;

import static org.isf.utils.Constants.DATE_TIME_FORMATTER;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.isf.generaldata.GeneralData;
import org.isf.generaldata.MessageBundle;
import org.isf.medicalinventory.manager.MedicalInventoryManager;
import org.isf.medicalinventory.manager.MedicalInventoryRowManager;
import org.isf.medicalinventory.model.InventoryStatus;
import org.isf.medicalinventory.model.InventoryType;
import org.isf.medicalinventory.model.MedicalInventory;
import org.isf.medicalinventory.model.MedicalInventoryRow;
import org.isf.medicals.manager.MedicalBrowsingManager;
import org.isf.medicals.model.Medical;
import org.isf.medicalstock.manager.MovStockInsertingManager;
import org.isf.medicalstock.model.Lot;
import org.isf.medstockmovtype.manager.MedicalDsrStockMovementTypeBrowserManager;
import org.isf.medstockmovtype.model.MovementType;
import org.isf.menu.manager.Context;
import org.isf.menu.manager.UserBrowsingManager;
import org.isf.stat.gui.report.GenericReportPharmaceuticalInventory;
import org.isf.stat.manager.JasperReportsManager;
import org.isf.supplier.manager.SupplierBrowserManager;
import org.isf.supplier.model.Supplier;
import org.isf.utils.db.NormalizeString;
import org.isf.utils.exception.OHServiceException;
import org.isf.utils.exception.gui.OHServiceExceptionUtil;
import org.isf.utils.jobjects.GoodDateChooser;
import org.isf.utils.jobjects.GoodDateTimeSpinnerChooser;
import org.isf.utils.jobjects.MessageDialog;
import org.isf.utils.jobjects.ModalJFrame;
import org.isf.utils.jobjects.RequestFocusListener;
import org.isf.utils.jobjects.TextPrompt;
import org.isf.utils.jobjects.TextPrompt.Show;
import org.isf.utils.time.TimeTools;
import org.isf.ward.manager.WardBrowserManager;
import org.isf.ward.model.Ward;

public class InventoryEdit extends ModalJFrame {

	private static final long serialVersionUID = 1L;

	private static EventListenerList InventoryListeners = new EventListenerList();

	public interface InventoryListener extends EventListener {

		public void InventoryInserted(AWTEvent e);

		public void InventoryUpdated(AWTEvent e);

		public void InventoryCancelled(AWTEvent e);
	}

	public static void addInventoryListener(InventoryListener listener) {
		InventoryListeners.add(InventoryListener.class, listener);
	}

	public static void removeInventoryListener(InventoryListener listener) {
		InventoryListeners.remove(InventoryListener.class, listener);
	}

	private void fireInventoryInserted() {
		AWTEvent event = new AWTEvent(new Object(), AWTEvent.RESERVED_ID_MAX + 1) {

			private static final long serialVersionUID = 1L;
		};

		EventListener[] listeners = InventoryListeners.getListeners(InventoryListener.class);
		for (int i = 0; i < listeners.length; i++) {
			((InventoryListener) listeners[i]).InventoryInserted(event);
		}
		if (jTableInventoryRow != null) {
			jTableInventoryRow.updateUI();
		}
	}

	private void fireInventoryUpdated() {
		AWTEvent event = new AWTEvent(new Object(), AWTEvent.RESERVED_ID_MAX + 1) {

			private static final long serialVersionUID = 1L;
		};

		EventListener[] listeners = InventoryListeners.getListeners(InventoryListener.class);
		for (int i = 0; i < listeners.length; i++) {
			((InventoryListener) listeners[i]).InventoryUpdated(event);
		}
		if (jTableInventoryRow != null) {
			jTableInventoryRow.updateUI();
		}
	}

	private GoodDateChooser jCalendarInventory;
	private LocalDateTime dateInventory = TimeTools.getNow();
	private JPanel panelHeader;
	private JPanel panelFooter;
	private JPanel panelContent;
	private JButton closeButton;
	private JButton printButton;
	private JButton deleteButton;
	private JButton saveButton;
	private JButton resetButton;
	private JButton lotButton;
	private JButton validateButton;
	private JButton confirmButton;
	private JScrollPane scrollPaneInventory;
	private JTable jTableInventoryRow;
	private DefaultTableModel model;
	private List<MedicalInventoryRow> inventoryRowList;
	private List<MedicalInventoryRow> inventoryRowSearchList = new ArrayList<>();
	private List<MedicalInventoryRow> inventoryRowListAdded = new ArrayList<>();
	private List<Lot> lotsSaved = new ArrayList<>();
	private HashMap<Integer, Lot> lotsDeleted = new HashMap<>();
	List<MedicalInventoryRow> inventoryRowsToDelete = new ArrayList<>();
	private String[] pColums = { MessageBundle.getMessage("angal.inventory.id.col").toUpperCase(),
			MessageBundle.getMessage("angal.common.code.txt").toUpperCase(),
			MessageBundle.getMessage("angal.inventory.product.col").toUpperCase(),
			MessageBundle.getMessage("angal.inventory.newlot.col").toUpperCase(),
			MessageBundle.getMessage("angal.wardpharmacy.lotnumber.col").toUpperCase(),
			MessageBundle.getMessage("angal.medicalstock.duedate.col").toUpperCase(),
			MessageBundle.getMessage("angal.inventoryrow.theoreticqty.col").toUpperCase(),
			MessageBundle.getMessage("angal.inventoryrow.realqty.col").toUpperCase(),
			MessageBundle.getMessage("angal.inventoryrow.unitprice.col").toUpperCase(),
			MessageBundle.getMessage("angal.inventory.totalprice").toUpperCase() };
	private int[] pColumwidth = { 50, 50, 200, 100, 100, 100, 100, 80, 80, 80 };
	private boolean[] columnEditable = { false, false, false, false, false, false, false, true, false, false };
	private boolean[] columnEditableView = { false, false, false, false, false, false, false, false, false, false };
	private boolean[] pColumnVisible = { false, true, true, true, !GeneralData.AUTOMATICLOT_IN, true, true, true, GeneralData.LOTWITHCOST,
			GeneralData.LOTWITHCOST };
	private MedicalInventory inventory = null;
	private JRadioButton specificRadio;
	private JRadioButton allRadio;
	private JLabel dateInventoryLabel;
	private JTextField codeTextField;
	private String code = null;
	private String mode = null;
	private JLabel referenceLabel;
	private JLabel statusLabel;
	private JLabel chargeTypeLabel;
	private JLabel dischargeTypeLabel;
	private JLabel supplierLabel;
	private JLabel destinationLabel;
	private JTextField referenceTextField;
	private JTextField jTextFieldEditor;
	private JComboBox<MovementType> chargeCombo;
	private JComboBox<MovementType> dischargeCombo;
	private JComboBox<Supplier> supplierCombo;
	private JComboBox<Ward> destinationCombo;
	private MovementType chargeType = null;
	private MovementType dischargeType = null;
	private Supplier supplier = null;
	private Ward destination = null;
	private String newReference = null;
	private MedicalInventoryManager medicalInventoryManager = Context.getApplicationContext().getBean(MedicalInventoryManager.class);
	private MedicalInventoryRowManager medicalInventoryRowManager = Context.getApplicationContext().getBean(MedicalInventoryRowManager.class);
	private MedicalBrowsingManager medicalBrowsingManager = Context.getApplicationContext().getBean(MedicalBrowsingManager.class);
	private MovStockInsertingManager movStockInsertingManager = Context.getApplicationContext().getBean(MovStockInsertingManager.class);
	private MedicalDsrStockMovementTypeBrowserManager movTypeManager = Context.getApplicationContext().getBean(MedicalDsrStockMovementTypeBrowserManager.class);
	private SupplierBrowserManager supplierManager = Context.getApplicationContext().getBean(SupplierBrowserManager.class);
	private WardBrowserManager wardManager = Context.getApplicationContext().getBean(WardBrowserManager.class);
	private JasperReportsManager jasperReportsManager = Context.getApplicationContext().getBean(JasperReportsManager.class);

	public InventoryEdit() {
		mode = "new";
		initComponents();
	}

	private boolean isAutomaticLotIn() {
		return GeneralData.AUTOMATICLOT_IN;
	}

	private boolean isLotWithCost() {
		return GeneralData.LOTWITHCOST;
	}

	public InventoryEdit(MedicalInventory inventory, String mod) {
		this.inventory = inventory;
		mode = mod;
		initComponents();
	}

	private void initComponents() {
		inventoryRowList = new ArrayList<>();
		inventoryRowSearchList = new ArrayList<>();
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setMinimumSize(new Dimension(1000, 600));
		setLocationRelativeTo(null);
		if (mode.equals("new")) {
			setTitle(MessageBundle.getMessage("angal.inventory.newinventory.title"));
		}
		if (mode.equals("view")) {
			setTitle(MessageBundle.getMessage("angal.inventory.viewinventory.title"));
		}
		if (mode.equals("update")) {
			setTitle(MessageBundle.getMessage("angal.inventory.editinventory.title"));
		}
		getContentPane().setLayout(new BorderLayout());

		panelHeader = getPanelHeader();

		getContentPane().add(panelHeader, BorderLayout.NORTH);

		panelContent = getPanelContent();
		getContentPane().add(panelContent, BorderLayout.CENTER);

		panelFooter = getPanelFooter();
		getContentPane().add(panelFooter, BorderLayout.SOUTH);

		addWindowListener(new WindowAdapter() {

			public void windowClosing(WindowEvent e) {
				closeButton.doClick();
			}
		});
		if (mode.equals("view")) {
			saveButton.setVisible(false);
			validateButton.setVisible(false);
			confirmButton.setVisible(false);
			deleteButton.setVisible(false);
			columnEditable = columnEditableView;
			codeTextField.setEditable(false);
			resetButton.setVisible(false);
			referenceTextField.setEditable(false);
			jCalendarInventory.setEnabled(false);
			specificRadio.setEnabled(false);
			allRadio.setEnabled(false);
			chargeCombo.setEnabled(false);
			dischargeCombo.setEnabled(false);
			supplierCombo.setEnabled(false);
			destinationCombo.setEnabled(false);
			lotButton.setVisible(false);
			printButton.setVisible(true);
		} else {
			saveButton.setVisible(true);
			validateButton.setVisible(true);
			deleteButton.setVisible(true);
			codeTextField.setEditable(true);
			resetButton.setVisible(true);
			referenceTextField.setEditable(true);
			jCalendarInventory.setEnabled(true);
			specificRadio.setEnabled(true);
			allRadio.setEnabled(true);
			chargeCombo.setEnabled(true);
			dischargeCombo.setEnabled(true);
			supplierCombo.setEnabled(true);
			destinationCombo.setEnabled(true);
			lotButton.setVisible(true);
			if (inventory != null && inventory.getStatus().equals(InventoryStatus.validated.toString())) {
				confirmButton.setEnabled(true);
			} else {
				confirmButton.setEnabled(false);
			}
			printButton.setVisible(false);
		}
	}

	private JPanel getPanelHeader() {
		if (panelHeader == null) {
			panelHeader = new JPanel();
			panelHeader.setBorder(new EmptyBorder(5, 0, 5, 0));
			GridBagLayout gbl_panelHeader = new GridBagLayout();
			gbl_panelHeader.columnWidths = new int[] { 159, 191, 192, 218, 218, 0 };
			gbl_panelHeader.rowHeights = new int[] { 30, 30, 30, 30, 0 };
			gbl_panelHeader.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
			gbl_panelHeader.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
			panelHeader.setLayout(gbl_panelHeader);
			GridBagConstraints gbc_dateInventoryLabel = new GridBagConstraints();
			gbc_dateInventoryLabel.anchor = GridBagConstraints.EAST;
			gbc_dateInventoryLabel.insets = new Insets(0, 0, 5, 5);
			gbc_dateInventoryLabel.gridx = 0;
			gbc_dateInventoryLabel.gridy = 0;
			panelHeader.add(getDateInventoryLabel(), gbc_dateInventoryLabel);
			GridBagConstraints gbc_jCalendarInventory = new GridBagConstraints();
			gbc_jCalendarInventory.fill = GridBagConstraints.HORIZONTAL;
			gbc_jCalendarInventory.insets = new Insets(0, 0, 5, 5);
			gbc_jCalendarInventory.gridx = 1;
			gbc_jCalendarInventory.gridy = 0;
			panelHeader.add(getJCalendarFrom(), gbc_jCalendarInventory);
			GridBagConstraints gbc_referenceLabel = new GridBagConstraints();
			gbc_referenceLabel.anchor = GridBagConstraints.EAST;
			gbc_referenceLabel.insets = new Insets(0, 0, 5, 5);
			gbc_referenceLabel.gridx = 2;
			gbc_referenceLabel.gridy = 0;
			panelHeader.add(getReferenceLabel(), gbc_referenceLabel);
			GridBagConstraints gbc_referenceTextField = new GridBagConstraints();
			gbc_referenceTextField.fill = GridBagConstraints.HORIZONTAL;
			gbc_referenceTextField.insets = new Insets(0, 0, 5, 5);
			gbc_referenceTextField.gridx = 3;
			gbc_referenceTextField.gridy = 0;
			panelHeader.add(getReferenceTextField(), gbc_referenceTextField);
			GridBagConstraints gbc_statusLabel = new GridBagConstraints();
			gbc_statusLabel.anchor = GridBagConstraints.CENTER;
			gbc_statusLabel.insets = new Insets(0, 0, 5, 5);
			gbc_statusLabel.gridx = 4;
			gbc_statusLabel.gridy = 0;
			gbc_statusLabel.gridheight = 3;
			panelHeader.add(getStatusLabel(), gbc_statusLabel);
			GridBagConstraints gbc_chargeLabel = new GridBagConstraints();
			gbc_chargeLabel.anchor = GridBagConstraints.EAST;
			gbc_chargeLabel.insets = new Insets(0, 0, 5, 5);
			gbc_chargeLabel.gridx = 0;
			gbc_chargeLabel.gridy = 1;
			panelHeader.add(getChargeLabel(), gbc_chargeLabel);
			GridBagConstraints gbc_jComboCharge = new GridBagConstraints();
			gbc_jComboCharge.fill = GridBagConstraints.HORIZONTAL;
			gbc_jComboCharge.insets = new Insets(0, 0, 5, 5);
			gbc_jComboCharge.gridx = 1;
			gbc_jComboCharge.gridy = 1;
			panelHeader.add(getJComboCharge(), gbc_jComboCharge);
			GridBagConstraints gbc_supplierLabel = new GridBagConstraints();
			gbc_supplierLabel.anchor = GridBagConstraints.EAST;
			gbc_supplierLabel.insets = new Insets(0, 0, 5, 5);
			gbc_supplierLabel.gridx = 2;
			gbc_supplierLabel.gridy = 1;
			panelHeader.add(getSupplierLabel(), gbc_supplierLabel);
			GridBagConstraints gbc_supplierCombo = new GridBagConstraints();
			gbc_supplierCombo.fill = GridBagConstraints.HORIZONTAL;
			gbc_supplierCombo.insets = new Insets(0, 0, 5, 5);
			gbc_supplierCombo.gridx = 3;
			gbc_supplierCombo.gridy = 1;
			panelHeader.add(getJComboSupplier(), gbc_supplierCombo);
			GridBagConstraints gbc_dischargeLabel = new GridBagConstraints();
			gbc_dischargeLabel.anchor = GridBagConstraints.EAST;
			gbc_dischargeLabel.insets = new Insets(0, 0, 5, 5);
			gbc_dischargeLabel.gridx = 0;
			gbc_dischargeLabel.gridy = 2;
			panelHeader.add(getDischargeLabel(), gbc_dischargeLabel);
			GridBagConstraints gbc_dichargeCombo = new GridBagConstraints();
			gbc_dichargeCombo.fill = GridBagConstraints.HORIZONTAL;
			gbc_dichargeCombo.insets = new Insets(0, 0, 5, 5);
			gbc_dichargeCombo.gridx = 1;
			gbc_dichargeCombo.gridy = 2;
			panelHeader.add(getJComboDischarge(), gbc_dichargeCombo);
			GridBagConstraints gbc_destinationLabel = new GridBagConstraints();
			gbc_destinationLabel.anchor = GridBagConstraints.EAST;
			gbc_destinationLabel.insets = new Insets(0, 0, 5, 5);
			gbc_destinationLabel.gridx = 2;
			gbc_destinationLabel.gridy = 2;
			panelHeader.add(getDestinationLabel(), gbc_destinationLabel);
			GridBagConstraints gbc_destinationCombo = new GridBagConstraints();
			gbc_destinationCombo.fill = GridBagConstraints.HORIZONTAL;
			gbc_destinationCombo.insets = new Insets(0, 0, 5, 5);
			gbc_destinationCombo.gridx = 3;
			gbc_destinationCombo.gridy = 2;
			panelHeader.add(getJComboDestination(), gbc_destinationCombo);
			GridBagConstraints gbc_specificRadio = new GridBagConstraints();
			gbc_specificRadio.anchor = GridBagConstraints.EAST;
			gbc_specificRadio.insets = new Insets(0, 0, 0, 5);
			gbc_specificRadio.gridx = 0;
			gbc_specificRadio.gridy = 3;
			panelHeader.add(getSpecificRadio(), gbc_specificRadio);
			GridBagConstraints gbc_codeTextField = new GridBagConstraints();
			gbc_codeTextField.insets = new Insets(0, 0, 0, 5);
			gbc_codeTextField.fill = GridBagConstraints.HORIZONTAL;
			gbc_codeTextField.gridx = 1;
			gbc_codeTextField.gridy = 3;
			panelHeader.add(getCodeTextField(), gbc_codeTextField);
			GridBagConstraints gbc_allRadio = new GridBagConstraints();
			gbc_allRadio.anchor = GridBagConstraints.EAST;
			gbc_allRadio.insets = new Insets(0, 0, 0, 5);
			gbc_allRadio.gridx = 2;
			gbc_allRadio.gridy = 3;
			panelHeader.add(getAllRadio(), gbc_allRadio);
			ButtonGroup group = new ButtonGroup();
			group.add(specificRadio);
			group.add(allRadio);
		}
		return panelHeader;
	}

	private JPanel getPanelContent() {
		if (panelContent == null) {
			panelContent = new JPanel();
			GridBagLayout gbl_panelContent = new GridBagLayout();
			gbl_panelContent.columnWidths = new int[] { 452, 0 };
			gbl_panelContent.rowHeights = new int[] { 402, 0 };
			gbl_panelContent.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
			gbl_panelContent.rowWeights = new double[] { 1.0, Double.MIN_VALUE };
			panelContent.setLayout(gbl_panelContent);
			GridBagConstraints gbc_scrollPaneInventory = new GridBagConstraints();
			gbc_scrollPaneInventory.fill = GridBagConstraints.BOTH;
			gbc_scrollPaneInventory.gridx = 0;
			gbc_scrollPaneInventory.gridy = 0;
			panelContent.add(getScrollPaneInventory(), gbc_scrollPaneInventory);
		}
		return panelContent;
	}

	private JPanel getPanelFooter() {
		if (panelFooter == null) {
			panelFooter = new JPanel();
			panelFooter.add(getSaveButton());
			panelFooter.add(getLotButton());
			panelFooter.add(getDeleteButton());
			panelFooter.add(getCleanTableButton());
			panelFooter.add(getValidateButton());
			panelFooter.add(getConfirmButton());
			panelFooter.add(getPrintButton());
			panelFooter.add(getCloseButton());
		}
		return panelFooter;
	}

	private GoodDateChooser getJCalendarFrom() {
		if (jCalendarInventory == null) {
			jCalendarInventory = new GoodDateChooser(LocalDate.now(), false, false);
			if (inventory != null) {
				jCalendarInventory.setDate(inventory.getInventoryDate().toLocalDate());
				dateInventory = inventory.getInventoryDate();
			}
			jCalendarInventory.addDateChangeListener(event -> {
				dateInventory = jCalendarInventory.getDate().atStartOfDay();
			});
		}
		return jCalendarInventory;
	}

	private JButton getSaveButton() {
		saveButton = new JButton(MessageBundle.getMessage("angal.common.save.btn"));
		saveButton.setMnemonic(MessageBundle.getMnemonic("angal.common.save.btn.key"));
		saveButton.addActionListener(actionEvent -> {
			String status = InventoryStatus.draft.toString();
			String user = UserBrowsingManager.getCurrentUser();
			if (inventoryRowSearchList == null || inventoryRowSearchList.isEmpty()) {
				MessageDialog.error(null, "angal.inventory.cannotsaveinventorywithoutproducts.msg");
				return;
			}
			try {
				if (!lotsDeleted.isEmpty() || !inventoryRowsToDelete.isEmpty()) {
					for (Map.Entry<Integer, Lot> entry : lotsDeleted.entrySet()) {
						MedicalInventoryRow invRow = medicalInventoryRowManager.getMedicalInventoryRowById(entry.getKey());
						if (invRow != null) {
							invRow.setLot(null);
							medicalInventoryRowManager.updateMedicalInventoryRow(invRow);
							movStockInsertingManager.deleteLot(entry.getValue());
						}

					}
					medicalInventoryRowManager.deleteMedicalInventoryRows(inventoryRowsToDelete);
				}
				ListIterator<MedicalInventoryRow> inventoryRowSearchListIterator = inventoryRowSearchList.listIterator();
				List<MedicalInventoryRow> newMedicalInventoryRows = new ArrayList<>();
				if (mode.equals("new")) {
					newReference = referenceTextField.getText().trim();
					boolean refExist = false;
					refExist = medicalInventoryManager.referenceExists(newReference);
					boolean referenceExistonMovementStock = movStockInsertingManager.refNoExists(newReference);
					if (refExist || referenceExistonMovementStock) {
						MessageDialog.error(null, "angal.inventory.referencealreadyused.msg");
						return;
					}
					inventory = new MedicalInventory();
					inventory.setInventoryReference(newReference);
					inventory.setInventoryDate(dateInventory);
					inventory.setStatus(status);
					inventory.setUser(user);
					inventory.setInventoryType(InventoryType.main.toString());
					inventory.setChargeType(chargeType == null ? null : chargeType.getCode());
					inventory.setDischargeType(dischargeType == null ? null : dischargeType.getCode());
					inventory.setSupplier(supplier == null ? null : supplier.getSupId());
					inventory.setDestination(destination == null ? null : destination.getCode());
					inventory = medicalInventoryManager.newMedicalInventory(inventory);
					while (inventoryRowSearchListIterator.hasNext()) {
						MedicalInventoryRow medicalInventoryRow = inventoryRowSearchListIterator.next();
						medicalInventoryRow.setInventory(inventory);
						Lot lot = medicalInventoryRow.getLot();
						String lotCode;
						Medical medical = medicalInventoryRow.getMedical();
						if (lot != null) {
							lotCode = lot.getCode();
							Lot lotExist = null;
							lotExist = movStockInsertingManager.getLot(lotCode);
							if (lotExist != null) {
								Lot lotStore = null;
								lotStore = movStockInsertingManager.updateLot(lot);
								medicalInventoryRow.setLot(lotStore);
							} else {
								if (lot.getDueDate() != null) {
									Lot lotStore = null;
									lotStore = movStockInsertingManager.storeLot(lotCode, lot, medical);
									medicalInventoryRow.setLot(lotStore);
									medicalInventoryRow.setNewLot(true);
								} else {
									medicalInventoryRow.setLot(null);
								}
							}
						} else {
							medicalInventoryRow.setLot(null);
						}
						medicalInventoryRow = medicalInventoryRowManager.newMedicalInventoryRow(medicalInventoryRow);
						newMedicalInventoryRows.add(medicalInventoryRow);
					}
					mode = "update";
					MessageDialog.info(this, "angal.inventory.savesuccess.msg");
					fireInventoryInserted();
					validateButton.setEnabled(true);
					resetVariable();
				} else if (mode.equals("update")) {
					String lastCharge = inventory.getChargeType();
					String lastDischarge = inventory.getDischargeType();
					Integer lastSupplier = inventory.getSupplier();
					inventory.setStatus(status);
					String lastDestination = inventory.getDestination();
					String lastReference = inventory.getInventoryReference();
					newReference = referenceTextField.getText().trim();
					LocalDateTime lastDate = inventory.getInventoryDate();
					MedicalInventory existingInventory = medicalInventoryManager.getInventoryByReference(newReference);
					boolean referenceExistonMovementStock = movStockInsertingManager.refNoExists(newReference);
					if ((existingInventory != null && existingInventory.getId() != inventory.getId()) || referenceExistonMovementStock) {
						MessageDialog.error(null, "angal.inventory.referencealreadyused.msg");
						return;
					}
					if (!inventory.getInventoryDate().equals(dateInventory)) {
						inventory.setInventoryDate(dateInventory);
					}
					if (!inventory.getUser().equals(user)) {
						inventory.setUser(user);
					}
					if (!lastReference.equals(newReference)) {
						inventory.setInventoryReference(newReference);
					}
					MovementType charge = (MovementType) chargeCombo.getSelectedItem();
					MovementType discharge = (MovementType) dischargeCombo.getSelectedItem();
					Supplier supplier = (Supplier) supplierCombo.getSelectedItem();
					Ward destination = (Ward) destinationCombo.getSelectedItem();
					inventory.setChargeType(charge == null ? null : charge.getCode());
					inventory.setDischargeType(discharge == null ? null : discharge.getCode());
					inventory.setSupplier(supplier == null ? null : supplier.getSupId());
					inventory.setDestination(destination == null ? null : destination.getCode());
					if (inventoryRowListAdded.isEmpty() && lotsSaved.isEmpty() && lotsDeleted.isEmpty()) {
						if (checkParameters(lastDestination, lastCharge, lastDischarge, lastSupplier, lastReference, lastDate)) {
							int response = MessageDialog.yesNo(null, "angal.inventory.doyouwanttoupdatethisinventory.msg");
							if (response == JOptionPane.YES_OPTION) {
								inventory = medicalInventoryManager.updateMedicalInventory(inventory, true);
								if (inventory != null) {
									MessageDialog.info(null, "angal.inventory.update.success.msg");
									statusLabel.setText(status.toUpperCase());
									statusLabel.setForeground(Color.GRAY);
									fireInventoryUpdated();
									validateButton.setEnabled(true);
									if (confirmButton.isEnabled()) {
										confirmButton.setEnabled(false);
									}
									if (areAllMedicalsInInventory()) {
										allRadio.setSelected(true);
										specificRadio.setSelected(false);
										codeTextField.setEnabled(false);
									} else {
										specificRadio.setSelected(true);
										codeTextField.setEnabled(true);
										allRadio.setSelected(false);
									}
									resetVariable();
								} else {
									MessageDialog.error(null, "angal.inventory.update.error.msg");
									return;
								}
							}
						} else {
							if (!inventoryRowsToDelete.isEmpty()) {
								MessageDialog.info(null, "angal.inventory.update.success.msg");
								statusLabel.setText(status.toUpperCase());
								statusLabel.setForeground(Color.GRAY);
								fireInventoryUpdated();
								validateButton.setEnabled(true);
								if (confirmButton.isEnabled()) {
									confirmButton.setEnabled(false);
								}
								if (areAllMedicalsInInventory()) {
									allRadio.setSelected(true);
									specificRadio.setSelected(false);
									codeTextField.setEnabled(false);
								} else {
									specificRadio.setSelected(true);
									codeTextField.setEnabled(true);
									allRadio.setSelected(false);
								}
								resetVariable();
							} else {
								MessageDialog.info(null, "angal.inventory.nothinghasbeenaddedonthisinventory.msg");
							}
						}
						return;
					}
					int response = MessageDialog.yesNo(null, "angal.inventory.doyouwanttoupdatethisinventory.msg");
					if (response == JOptionPane.YES_OPTION) {
						inventory = medicalInventoryManager.updateMedicalInventory(inventory, true);
						while (inventoryRowSearchListIterator.hasNext()) {
							MedicalInventoryRow medicalInventoryRow = inventoryRowSearchListIterator.next();
							Medical medical = medicalInventoryRow.getMedical();
							Lot lot = medicalInventoryRow.getLot();
							String lotCode;
							if (lot != null) {
								lotCode = lot.getCode();
								Lot lotExist = movStockInsertingManager.getLot(lotCode);
								if (lotExist != null) {
									lot.setMedical(medical);
									lot = movStockInsertingManager.updateLot(lot);
									medicalInventoryRow.setLot(lot);
								} else {
									int idInvRow = medicalInventoryRow.getId();
									MedicalInventoryRow invRow = medicalInventoryRowManager.getMedicalInventoryRowById(idInvRow);
									if (invRow.getLock() != medicalInventoryRow.getLock()) {
										Lot newLot = movStockInsertingManager.storeLot(lotCode, lot, medical);
										invRow.setLot(newLot);
										invRow.setNewLot(true);
										invRow.setRealqty(medicalInventoryRow.getRealQty());
										medicalInventoryRow = invRow;
									} else {
										Lot newLot = movStockInsertingManager.storeLot(lotCode, lot, medical);
										medicalInventoryRow.setLot(newLot);
										medicalInventoryRow.setNewLot(true);
									}
									
								}
							}
							medicalInventoryRow.setInventory(inventory);
							int id = medicalInventoryRow.getId();
							if (id == 0) {
								medicalInventoryRow = medicalInventoryRowManager.newMedicalInventoryRow(medicalInventoryRow);
							} else {
								medicalInventoryRow = medicalInventoryRowManager.updateMedicalInventoryRow(medicalInventoryRow);
							}
							newMedicalInventoryRows.add(medicalInventoryRow);
						}
						MessageDialog.info(null, "angal.inventory.update.success.msg");
						statusLabel.setText(status.toUpperCase());
						statusLabel.setForeground(Color.GRAY);
						resetVariable();
						fireInventoryUpdated();
						validateButton.setEnabled(true);
						if (areAllMedicalsInInventory()) {
							allRadio.setSelected(true);
							specificRadio.setSelected(false);
						} else {
							specificRadio.setSelected(true);
							allRadio.setSelected(false);
						}
					}
				}
				inventoryRowSearchList = newMedicalInventoryRows;
				jTableInventoryRow.updateUI();
			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
				return;
			}
		});
		return saveButton;
	}
	private JButton getDeleteButton() {
		deleteButton = new JButton(MessageBundle.getMessage("angal.common.delete.btn"));
		deleteButton.setMnemonic(MessageBundle.getMnemonic("angal.common.delete.btn.key"));
		deleteButton.addActionListener(actionEvent -> {
			int[] selectedRows = jTableInventoryRow.getSelectedRows();
			if (selectedRows.length == 0) {
				MessageDialog.error(this, "angal.inventoryrow.pleaseselectatleastoneinventoryrow.msg");
				return;
			}
			int delete = MessageDialog.yesNo(null, "angal.inventoryrow.doyoureallywanttodeletethisinventoryrow.msg");
			if (delete == JOptionPane.YES_OPTION) {
				if (selectedRows.length == inventoryRowSearchList.size()) {
					resetButton.doClick();
					return;
				}
				if (inventory == null) {
					for (int i = selectedRows.length - 1; i >= 0; i--) {
						MedicalInventoryRow selectedInventoryRow = (MedicalInventoryRow) jTableInventoryRow.getValueAt(selectedRows[i], -1);
						inventoryRowSearchList.remove(selectedInventoryRow);
						model.fireTableDataChanged();
						jTableInventoryRow.setModel(model);
					}

				} else {
					for (int i = selectedRows.length - 1; i >= 0; i--) {
						MedicalInventoryRow inventoryRow = (MedicalInventoryRow) jTableInventoryRow.getValueAt(selectedRows[i], -1);
						inventoryRowSearchList.remove(inventoryRow);
						inventoryRowsToDelete.add(inventoryRow);
						model.fireTableDataChanged();
						jTableInventoryRow.setModel(model);
					}
				}
				jTableInventoryRow.clearSelection();
				adjustWidth();
			} else {
				return;
			}
		});
		return deleteButton;
	}

	private JButton getLotButton() {
		lotButton = new JButton(MessageBundle.getMessage("angal.inventory.lot.btn"));
		lotButton.setMnemonic(MessageBundle.getMnemonic("angal.inventory.lot.btn.key"));
		lotButton.addActionListener(actionEvent -> {
			int selectedRow = jTableInventoryRow.getSelectedRow();
			if (selectedRow == -1) {
				MessageDialog.error(this, "angal.inventoryrow.pleaseselectoneinventoryrow.msg");
				return;
			}
			MedicalInventoryRow selectedInventoryRow = (MedicalInventoryRow) jTableInventoryRow.getValueAt(selectedRow, -1);
			Lot lotToUpdate = selectedInventoryRow.getLot();
			Lot lot = new Lot();
			try {
				if (lotToUpdate != null && !selectedInventoryRow.isNewLot()) {
					BigDecimal cost = BigDecimal.ZERO;
					if (isLotWithCost()) {
						cost = askCost(2, cost);
						if (cost.compareTo(BigDecimal.ZERO) == 0) {
							return;
						}
					}
					lotToUpdate.setCost(cost);
					lot = lotToUpdate;
				} else {
					lot = this.getLot(lotToUpdate);
					String lotCode = lotToUpdate != null ? lotToUpdate.getCode() : "";
					if (lot != null && !lot.getCode().equals(lotCode)) {
						Lot lotDelete = movStockInsertingManager.getLot(lotCode);
						if (lotDelete != null) {
							lotsDeleted.put(selectedInventoryRow.getId(), lotDelete);
						}
					}
				}

			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
				return;
			}
			if (lot != null) {
				code = lot.getCode();
				if (selectedInventoryRow.getLot() == null) {
					List<MedicalInventoryRow> invRows = inventoryRowSearchList.stream()
									.filter(inv -> inv.getLot() != null && inv.getLot().getCode().equals(code)).collect(Collectors.toList());
					if (invRows.size() == 0 || code.equals("")) {
						selectedInventoryRow.setNewLot(true);
						selectedInventoryRow.setLot(lot);
						lotsSaved.add(lot);
					} else {
						if (lotToUpdate != null && code.equals(lotToUpdate.getCode())) {
							selectedInventoryRow.setLot(lot);
							lotsSaved.add(lot);
						} else {
							MessageDialog.error(this, "angal.inventoryrow.thislotcodealreadyexists.msg");
							lotButton.doClick();
						}
					}
				} else {
					List<MedicalInventoryRow> invRows = inventoryRowSearchList.stream()
									.filter(inv -> inv.getMedical().getCode().equals(selectedInventoryRow.getMedical().getCode())).collect(Collectors.toList());
					invRows = invRows.stream().filter(inv -> inv.getLot() != null && inv.getLot().getCode().equals(code)).collect(Collectors.toList());
					if (invRows.size() == 0 || code.equals("")) {
						selectedInventoryRow.setNewLot(true);
						selectedInventoryRow.setLot(lot);
						lotsSaved.add(lot);
					} else {
						if (lotToUpdate != null && code.equals(lotToUpdate.getCode())) {
							selectedInventoryRow.setLot(lot);
							lotsSaved.add(lot);
						} else {
							MessageDialog.error(this, "angal.inventoryrow.thislotcodealreadyexists.msg");
							lotButton.doClick();
						}
					}
				}
				inventoryRowSearchList.set(selectedRow, selectedInventoryRow);
				jTableInventoryRow.updateUI();
			}
		});
		lotButton.setEnabled(false);
		return lotButton;
	}

	private JButton getCloseButton() {
		closeButton = new JButton(MessageBundle.getMessage("angal.common.close.btn"));
		closeButton.setMnemonic(MessageBundle.getMnemonic("angal.common.close.btn.key"));
		closeButton.addActionListener(actionEvent -> {
			String lastCharge = null;
			String lastDischarge = null;
			Integer lastSupplier = null;
			String lastDestination = null;
			String lastReference = null;
			newReference = referenceTextField.getText().trim();
			LocalDateTime lastDate = dateInventory;
			if (inventory != null) {
				lastCharge = inventory.getChargeType();
				lastDischarge = inventory.getDischargeType();
				lastSupplier = inventory.getSupplier();
				lastDestination = inventory.getDestination();
				lastReference = inventory.getInventoryReference();
				lastDate = inventory.getInventoryDate();
			}
			if (checkParameters(lastDestination, lastCharge, lastDischarge, lastSupplier, lastReference, lastDate)) {
				int reset = MessageDialog.yesNoCancel(null, "angal.inventoryrow.doyouwanttosavethechanges.msg");
				if (reset == JOptionPane.YES_OPTION) {
					this.saveButton.doClick();
				} else {
					resetVariable();
					dispose();
				}
			} else {
				resetVariable();
				dispose();
			}
		});
		return closeButton;
	}

	private JButton getPrintButton() {
		printButton = new JButton(MessageBundle.getMessage("angal.common.print.btn"));
		printButton.setMnemonic(MessageBundle.getMnemonic("angal.common.print.btn.key"));
		printButton.setEnabled(true);

		printButton.addActionListener(e -> {
			int printRealQty = 0;
			int response = MessageDialog.yesNo(this, "angal.inventory.askforrealquantityempty.msg");
			if (response == JOptionPane.YES_OPTION) {
				printRealQty = 1;
			}
			new GenericReportPharmaceuticalInventory(inventory, "Inventory", printRealQty);
		});

		return printButton;
	}

	private JButton getCleanTableButton() {
		resetButton = new JButton(MessageBundle.getMessage("angal.inventory.clean.btn"));
		resetButton.setMnemonic(MessageBundle.getMnemonic("angal.inventory.clean.btn.key"));
		resetButton.addActionListener(actionEvent -> {
			int reset = MessageDialog.yesNo(null, "angal.inventoryrow.doyoureallywanttocleanthistable.msg");
			if (reset == JOptionPane.YES_OPTION) {
				try {
					if (inventory != null) {
						List<MedicalInventoryRow> invRows = medicalInventoryRowManager.getMedicalInventoryRowByInventoryId(inventory.getId());
						medicalInventoryRowManager.deleteMedicalInventoryRows(invRows);
						inventoryRowSearchList.clear();
					} else {
						inventoryRowSearchList.clear();
					}
					
				} catch (OHServiceException e) {
					OHServiceExceptionUtil.showMessages(e);
				}
				specificRadio.setSelected(true);
				codeTextField.setEnabled(true);
				inventoryRowSearchList = new ArrayList<>();
				DefaultTableModel model = (DefaultTableModel) jTableInventoryRow.getModel();
				model.setRowCount(0);
				model.setColumnCount(0);
				jTableInventoryRow.updateUI();
				adjustWidth();
			}
		});
		return resetButton;
	}

	private JButton getValidateButton() {
		validateButton = new JButton(MessageBundle.getMessage("angal.inventory.validate.btn"));
		validateButton.setMnemonic(MessageBundle.getMnemonic("angal.inventory.validate.btn.key"));
		if (inventory == null) {
			validateButton.setEnabled(false);
		}
		validateButton.addActionListener(actionEvent -> {
			if (inventory == null) {
				MessageDialog.error(null, "angal.inventory.inventorymustsavebeforevalidation.msg");
				return;
			}
			List<MedicalInventoryRow> invRowWithoutLot = inventoryRowSearchList.stream().filter(invRow -> invRow.getLot() == null).collect(Collectors.toList());
			if (invRowWithoutLot.size() > 0) {
				MessageDialog.error(null, "angal.inventory.allinventoryrowshouldhavelotbeforevalidation.msg");
				return;
			}
			int reset = MessageDialog.yesNo(null, "angal.inventoryrow.doyoureallywanttovalidatethisinventory.msg");
			if (reset == JOptionPane.YES_OPTION) {
				String chargeCode = inventory.getChargeType();
				String dischargeCode = inventory.getDischargeType();
				Integer supplierId = inventory.getSupplier();
				String wardCode = inventory.getDestination();
				String lastReference = inventory.getInventoryReference();
				LocalDateTime lastDate = inventory.getInventoryDate();
				if (checkParameters(wardCode, chargeCode, dischargeCode, supplierId, lastReference, lastDate) || !lotsSaved.isEmpty() 
								|| !inventoryRowListAdded.isEmpty() || !inventoryRowsToDelete.isEmpty()) {
					saveButton.doClick();
					chargeCode = inventory.getChargeType();
					dischargeCode = inventory.getDischargeType();
					supplierId = inventory.getSupplier();
					wardCode = inventory.getDestination();
					lastReference = inventory.getInventoryReference();
					lastDate = inventory.getInventoryDate();
				}
				String errorMessage = this.checkParamsValues(chargeCode, dischargeCode, supplierId, wardCode);
				if (errorMessage != null) {
					MessageDialog.error(null, errorMessage);
					return;
				}
				// validate inventory
				try {
					medicalInventoryManager.validateMedicalInventoryRow(inventory, inventoryRowSearchList);
					String status = InventoryStatus.validated.toString();
					inventory.setStatus(status);
					inventory = medicalInventoryManager.updateMedicalInventory(inventory, true);
					MessageDialog.info(null, "angal.inventory.validate.success.msg");
					statusLabel.setText(status.toUpperCase());
					statusLabel.setForeground(Color.BLUE);
					confirmButton.setEnabled(true);
					fireInventoryUpdated();
				} catch (OHServiceException e) {
					OHServiceExceptionUtil.showMessages(e);
					int result = MessageDialog.yesNo(null, "angal.inventory.doyouwanttoactualizetheinventory.msg");
					if (result == JOptionPane.YES_OPTION) {
						try {
							String status = InventoryStatus.validated.toString();
							inventory.setStatus(status);
							medicalInventoryManager.actualizeMedicalInventoryRow(inventory);
							statusLabel.setText(status.toUpperCase());
							statusLabel.setForeground(Color.BLUE);
							confirmButton.setEnabled(true);
							jTableInventoryRow.setModel(new InventoryRowModel());
							fireInventoryUpdated();
							adjustWidth();
						} catch (OHServiceException e1) {
							OHServiceExceptionUtil.showMessages(e);
						}
					}
				}
			}
		});
		return validateButton;
	}

	private JButton getConfirmButton() {
		confirmButton = new JButton(MessageBundle.getMessage("angal.inventory.confirm.btn"));
		confirmButton.setMnemonic(MessageBundle.getMnemonic("angal.inventory.confirm.btn.key"));
		if (inventory == null) {
			confirmButton.setEnabled(false);
		}
		confirmButton.addActionListener(actionEvent -> {
			if (inventory == null) {
				MessageDialog.error(null, "angal.inventory.inventorymustsavebeforevalidation.msg");
				return;
			}
			List<MedicalInventoryRow> invRowWithoutLot = inventoryRowSearchList.stream().filter(invRow -> invRow.getLot() == null).collect(Collectors.toList());
			if (invRowWithoutLot.size() > 0) {
				MessageDialog.error(null, "angal.inventory.allinventoryrowshouldhavelotbeforevalidation.msg");
				return;
			}
			int confirm = MessageDialog.yesNo(null, "angal.inventory.doyoureallywanttoconfirmthisinventory.msg");
			if (confirm == JOptionPane.YES_OPTION) {
				String dischargeCode = inventory.getDischargeType();
				String chargeCode = inventory.getChargeType();
				Integer supplierId = inventory.getSupplier();
				String wardCode = inventory.getDestination();
				String errorMessage = this.checkParamsValues(chargeCode, dischargeCode, supplierId, wardCode);
				if (errorMessage != null) {
					MessageDialog.error(null, errorMessage);
					return;
				}
				// confirm inventory
				try {
					medicalInventoryManager.confirmMedicalInventoryRow(inventory, inventoryRowSearchList);
					MessageDialog.info(null, "angal.inventory.confirm.success.msg");
					fireInventoryUpdated();
					closeButton.doClick();
				} catch (OHServiceException e) {
					OHServiceExceptionUtil.showMessages(e);
					MessageDialog.info(null, "angal.inventory.pleasevalidateinventoryagainsbeforeconfirmation.msg");
					confirmButton.setEnabled(false);
					return;
				}
			}
		});
		return confirmButton;
	}

	private String checkParamsValues(String chargeCode, String dischargeCode, Integer supplierId, String wardCode) {
		if (chargeCode == null || chargeCode.isEmpty()) {
			return "angal.inventory.choosechargetypebeforevalidation.msg";
		}
		if (dischargeCode == null || dischargeCode.isEmpty()) {
			return "angal.inventory.choosedischargetypebeforevalidation.msg";
		}
		if (supplierId == null || supplierId == 0) {
			return "angal.inventory.choosesupplierbeforevalidation.msg";
		}
		if (wardCode == null || wardCode.isEmpty()) {
			return "angal.inventory.choosedestinationbeforevalidation.msg";
		}
		return null;
	}

	private JScrollPane getScrollPaneInventory() {
		if (scrollPaneInventory == null) {
			scrollPaneInventory = new JScrollPane();
			try {
				scrollPaneInventory.setViewportView(getJTableInventoryRow());
			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
			}
		}
		return scrollPaneInventory;
	}

	private JTable getJTableInventoryRow() throws OHServiceException {
		if (jTableInventoryRow == null) {
			jTableInventoryRow = new JTable();
			jTextFieldEditor = new JTextField();
			jTableInventoryRow.setFillsViewportHeight(true);
			model = new InventoryRowModel();
			jTableInventoryRow.setModel(model);
			for (int i = 0; i < pColumnVisible.length; i++) {
				jTableInventoryRow.getColumnModel().getColumn(i).setCellRenderer(new EnabledTableCellRenderer());
				jTableInventoryRow.getColumnModel().getColumn(i).setPreferredWidth(pColumwidth[i]);
				if (i == 0 || !pColumnVisible[i]) {
					jTableInventoryRow.getColumnModel().getColumn(i).setMinWidth(0);
					jTableInventoryRow.getColumnModel().getColumn(i).setMaxWidth(0);
					jTableInventoryRow.getColumnModel().getColumn(i).setPreferredWidth(0);
				}
			}
			DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
			centerRenderer.setHorizontalAlignment(JLabel.CENTER);
			jTableInventoryRow.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
			jTableInventoryRow.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

				@Override
				public void valueChanged(ListSelectionEvent e) {
					if (e.getValueIsAdjusting()) {
						int selectedRow = jTableInventoryRow.getSelectedRow();
						if (selectedRow != -1) {
							MedicalInventoryRow medInvRow = (MedicalInventoryRow) jTableInventoryRow.getValueAt(selectedRow, -1);
							Lot lot = medInvRow.getLot();
							if (lot == null) {
								lotButton.setEnabled(true);
							} else {
								BigDecimal cost = lot.getCost() != null ? lot.getCost() : BigDecimal.ZERO;
								if (lot != null) {
									if (isLotWithCost()) {
										if (cost.doubleValue() == 0.00 || medInvRow.isNewLot()) {
											lotButton.setEnabled(true);
										} else {
											lotButton.setEnabled(false);
										}
									} else {
										if (medInvRow.isNewLot()) {
											lotButton.setEnabled(true);
										} else {
											lotButton.setEnabled(false);
										}
									}

								}
							}
						} else {
							lotButton.setEnabled(false);
						}
					}
				}
			});
			DefaultCellEditor cellEditor = new DefaultCellEditor(jTextFieldEditor);
			jTableInventoryRow.setDefaultEditor(Integer.class, cellEditor);
		}
		return jTableInventoryRow;
	}

	class EnabledTableCellRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			return cell;
		}
	}

	class InventoryRowModel extends DefaultTableModel {

		private static final long serialVersionUID = 1L;

		public InventoryRowModel(boolean add) throws OHServiceException {
			inventoryRowList = loadNewInventoryTable(null, inventory, add);
			if (!inventoryRowList.isEmpty()) {
				for (MedicalInventoryRow invRow : inventoryRowList) {
					addMedInRowInInventorySearchList(invRow);
				}
			} else {
				MessageDialog.info(null, "angal.inventory.youhavealreadyaddedallproduct.msg");
			}
			if (areAllMedicalsInInventory()) {
				allRadio.setSelected(true);
				specificRadio.setSelected(false);
				codeTextField.setEnabled(false);
			} else {
				specificRadio.setSelected(true);
				codeTextField.setEnabled(true);
				allRadio.setSelected(false);
			}
		}

		public InventoryRowModel() throws OHServiceException {
			if (!inventoryRowSearchList.isEmpty()) {
				inventoryRowSearchList.clear();
			}
			if (inventory != null) {
				inventoryRowList = medicalInventoryRowManager.getMedicalInventoryRowByInventoryId(inventory.getId());
			} else {
				if (allRadio.isSelected()) {
					inventoryRowList = loadNewInventoryTable(null, inventory, false);
				}
			}
			if (!inventoryRowList.isEmpty()) {
				for (MedicalInventoryRow invRow : inventoryRowList) {
					addMedInRowInInventorySearchList(invRow);
					if (invRow.getId() == 0) {
						inventoryRowListAdded.add(invRow);
					}
				}
			}
			if (areAllMedicalsInInventory()) {
				allRadio.setSelected(true);
				specificRadio.setSelected(false);
				codeTextField.setEnabled(false);
			} else {
				specificRadio.setSelected(true);
				codeTextField.setEnabled(true);
				allRadio.setSelected(false);
			}
		}

		public Class< ? > getColumnClass(int c) {
			if (c == 0) {
				return Integer.class;
			} else if (c == 1) {
				return String.class;
			} else if (c == 2) {
				return String.class;
			} else if (c == 3) {
				return String.class;
			} else if (c == 4) {
				return String.class;
			} else if (c == 5) {
				return String.class;
			} else if (c == 6) {
				return Double.class;
			} else if (c == 7) {
				return Double.class;
			} else if (c == 8) {
				return BigDecimal.class;
			} else if (c == 9) {
				return Double.class;
			}
			return null;
		}

		public int getRowCount() {
			if (inventoryRowSearchList == null) {
				return 0;
			}
			return inventoryRowSearchList.size();
		}

		public String getColumnName(int c) {
			return pColums[c];
		}

		public int getColumnCount() {
			return pColums.length;
		}

		public Object getValueAt(int r, int c) {
			if (r < inventoryRowSearchList.size()) {
				MedicalInventoryRow medInvtRow = inventoryRowSearchList.get(r);
				if (c == -1) {
					return medInvtRow;
				} else if (c == 0) {
					return medInvtRow.getId();
				} else if (c == 1) {
					return medInvtRow.getMedical() == null ? "" : medInvtRow.getMedical().getProdCode();
				} else if (c == 2) {
					return medInvtRow.getMedical() == null ? "" : medInvtRow.getMedical().getDescription();
				} else if (c == 3) {
					if (medInvtRow.getLot() == null || medInvtRow.isNewLot()) {
						return "N";
					}
					return "";
				} else if (c == 4) {
					if (medInvtRow.getLot() == null) {
						return "";
					}
					return medInvtRow.getLot().getCode().equals("") ? "AUTO" : medInvtRow.getLot().getCode();
				} else if (c == 5) {
					if (medInvtRow.getLot() != null) {
						if (medInvtRow.getLot().getDueDate() != null) {
							return medInvtRow.getLot().getDueDate().format(DATE_TIME_FORMATTER);
						}
					}
					return "";
				} else if (c == 6) {
					Double dblVal = medInvtRow.getTheoreticQty();
					return dblVal.intValue();
				} else if (c == 7) {
					Double dblValue = medInvtRow.getRealQty();
					return dblValue.intValue();
				} else if (c == 8) {
					if (medInvtRow.getLot() != null) {
						if (medInvtRow.getLot().getCost() != null) {
							medInvtRow.setTotal(medInvtRow.getRealQty() * medInvtRow.getLot().getCost().doubleValue());
							return medInvtRow.getLot().getCost();
						}
					}
					return new BigDecimal("0.00");
				} else if (c == 9) {
					if (medInvtRow.getLot() != null) {
						if (medInvtRow.getLot().getCost() != null) {
							return medInvtRow.getTotal();
						}
					}
					return 0.0;
				}
			}
			return null;
		}

		@Override
		public void setValueAt(Object value, int r, int c) {
			if (r < inventoryRowSearchList.size()) {
				MedicalInventoryRow invRow = inventoryRowSearchList.get(r);
				if (c == 7) {
					Double intValue = 0.0;
					if (value != null) {
						try {
							intValue = Double.parseDouble(value.toString());
						} catch (NumberFormatException e) {
							intValue = 0.0;
							return;
						}
					}
					if (intValue < 0) {
						MessageDialog.error(null, "angal.inventoryrow.invalidquantity.msg");
						return;
					}
					invRow.setRealqty(intValue);
					if (invRow.getLot() != null && invRow.getLot().getCost() != null) {
						double total = invRow.getRealQty() * invRow.getLot().getCost().doubleValue();
						invRow.setTotal(total);
					}
					inventoryRowListAdded.add(invRow);
					inventoryRowSearchList.set(r, invRow);
					SwingUtilities.invokeLater(() -> {
						jTableInventoryRow.updateUI();
					});
				}
			}

		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnEditable[columnIndex];
		}
	}

	private void adjustWidth() {
		for (int i = 0; i < jTableInventoryRow.getColumnModel().getColumnCount(); i++) {
			jTableInventoryRow.getColumnModel().getColumn(i).setPreferredWidth(pColumwidth[i]);
			if (i == 0 || !pColumnVisible[i]) {
				jTableInventoryRow.getColumnModel().getColumn(i).setMinWidth(0);
				jTableInventoryRow.getColumnModel().getColumn(i).setMaxWidth(0);
				jTableInventoryRow.getColumnModel().getColumn(i).setPreferredWidth(0);
			}
		}
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment(JLabel.CENTER);
		jTableInventoryRow.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
	}

	private Lot getLot(Lot lotToUpdate) throws OHServiceException {
		Lot lot = null;
		if (isAutomaticLotIn()) {
			LocalDateTime preparationDate = TimeTools.getNow().truncatedTo(ChronoUnit.MINUTES);
			LocalDateTime expiringDate = askExpiringDate();
			lot = new Lot("", preparationDate, expiringDate);
			// Cost
			BigDecimal cost = new BigDecimal(0);
			if (isLotWithCost()) {
				cost = askCost(2, cost);
				if (cost.compareTo(new BigDecimal(0)) == 0) {
					return null;
				}
			}
			lot.setCost(cost);
		} else {
			lot = askLot(lotToUpdate);
		}
		return lot;
	}

	private Lot askLot(Lot lotToUpdate) {
		LocalDateTime preparationDate;
		LocalDateTime expiringDate;
		Lot lot = null;

		JTextField lotNameTextField = new JTextField(15);
		lotNameTextField.addAncestorListener(new RequestFocusListener());
		TextPrompt suggestion = new TextPrompt(MessageBundle.getMessage("angal.medicalstock.multiplecharging.lotid"), lotNameTextField);
		suggestion.setFont(new Font("Tahoma", Font.PLAIN, 14));
		suggestion.setForeground(Color.GRAY);
		suggestion.setHorizontalAlignment(SwingConstants.CENTER);
		suggestion.changeAlpha(0.5f);
		suggestion.changeStyle(Font.BOLD + Font.ITALIC);
		LocalDate now = LocalDate.now();
		GoodDateChooser preparationDateChooser = new GoodDateChooser(now);
		GoodDateChooser expireDateChooser = new GoodDateChooser(now);
		if (lotToUpdate != null) {
			lotNameTextField.setText(lotToUpdate.getCode());
			preparationDateChooser = new GoodDateChooser(lotToUpdate.getPreparationDate().toLocalDate());
			expireDateChooser = new GoodDateChooser(lotToUpdate.getDueDate().toLocalDate());
		}
		JPanel panel = new JPanel(new GridLayout(3, 2));
		panel.add(new JLabel(MessageBundle.getMessage("angal.medicalstock.multiplecharging.lotnumberabb")));
		panel.add(lotNameTextField);
		panel.add(new JLabel(MessageBundle.getMessage("angal.medicalstock.multiplecharging.preparationdate")));
		panel.add(preparationDateChooser);
		panel.add(new JLabel(MessageBundle.getMessage("angal.medicalstock.multiplecharging.expiringdate")));
		panel.add(expireDateChooser);
		do {
			int ok = JOptionPane.showConfirmDialog(
							this,
							panel,
							MessageBundle.getMessage("angal.medicalstock.multiplecharging.lotinformations"),
							JOptionPane.OK_CANCEL_OPTION);

			if (ok == JOptionPane.OK_OPTION) {
				String lotName = lotNameTextField.getText();
				if (expireDateChooser.getDate().isBefore(preparationDateChooser.getDate())) {
					MessageDialog.error(this, "angal.medicalstock.multiplecharging.expirydatebeforepreparationdate");
				} else {
					expiringDate = expireDateChooser.getDateEndOfDay();
					preparationDate = preparationDateChooser.getDateStartOfDay();
					lot = new Lot(lotName, preparationDate, expiringDate);
					BigDecimal cost = new BigDecimal(0);
					if (isLotWithCost()) {
						if (lotToUpdate != null) {
							cost = askCost(2, lotToUpdate.getCost());
						} else {
							cost = askCost(2, cost);
						}

						if (cost.compareTo(new BigDecimal(0)) == 0) {
							return null;
						} else {
							lot.setCost(cost);
						}
					}
				}
			} else {
				return null;
			}
		} while (lot == null);
		return lot;
	}
	private BigDecimal askCost(int qty, BigDecimal lastCost) {
		double cost = 0.;
		do {
			String input = JOptionPane.showInputDialog(this,
							MessageBundle.getMessage("angal.medicalstock.multiplecharging.unitcost"),
							lastCost);
			if (input != null) {
				try {
					cost = Double.parseDouble(input);
					if (cost < 0) {
						throw new NumberFormatException();
					} else if (cost == 0.) {
						double total = askTotalCost();
						cost = total / qty;
					}
				} catch (NumberFormatException nfe) {
					MessageDialog.error(this, "angal.medicalstock.multiplecharging.pleaseinsertavalidvalue");
				}
			} else {
				return BigDecimal.valueOf(cost);
			}
		} while (cost == 0.);
		return BigDecimal.valueOf(cost);
	}

	protected LocalDateTime askExpiringDate() {
		LocalDateTime date = TimeTools.getNow();
		GoodDateTimeSpinnerChooser expireDateChooser = new GoodDateTimeSpinnerChooser(date);
		JPanel panel = new JPanel(new GridLayout(1, 2));
		panel.add(new JLabel(MessageBundle.getMessage("angal.medicalstock.multiplecharging.expiringdate")));
		panel.add(expireDateChooser);

		int ok = JOptionPane.showConfirmDialog(this, panel,
						MessageBundle.getMessage("angal.medicalstock.multiplecharging.expiringdate"),
						JOptionPane.OK_CANCEL_OPTION);

		if (ok == JOptionPane.OK_OPTION) {
			date = expireDateChooser.getLocalDateTime();
		}
		return date;
	}

	protected double askTotalCost() {
		String input = JOptionPane.showInputDialog(this,
						MessageBundle.getMessage("angal.medicalstock.multiplecharging.totalcost"),
						0.);
		double total = 0.;
		if (input != null) {
			try {
				total = Double.parseDouble(input);
				if (total < 0) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException nfe) {
				MessageDialog.error(this, "angal.medicalstock.multiplecharging.pleaseinsertavalidvalue");
			}
		}
		return total;
	}

	public MedicalInventory getInventory() {
		return inventory;
	}

	public void setInventory(MedicalInventory inventory) {
		this.inventory = inventory;
	}

	private JRadioButton getSpecificRadio() {
		if (specificRadio == null) {
			specificRadio = new JRadioButton(MessageBundle.getMessage("angal.inventory.specificproduct.txt"));
			specificRadio.addActionListener(actionEvent -> {
				if (specificRadio.isSelected()) {
					codeTextField.setEnabled(true);
					codeTextField.setText("");
					allRadio.setSelected(false);
				}
			});
		}
		return specificRadio;
	}

	private JRadioButton getAllRadio() {
		if (allRadio == null) {
			allRadio = new JRadioButton(MessageBundle.getMessage("angal.inventory.allproduct.txt"));
			allRadio.addActionListener(actionEvent -> {
				try {
					if (!areAllMedicalsInInventory()) {
						codeTextField.setEnabled(false);
						codeTextField.setText("");
						if (inventoryRowSearchList.size() > 0) {
							int info = MessageDialog.yesNo(null, "angal.inventoryrow.doyouwanttoaddallnotyetlistedproducts.msg");
							if (info == JOptionPane.YES_OPTION) {
								try {
									allRadio.setSelected(true);
									jTableInventoryRow.setModel(new InventoryRowModel(true));
								} catch (OHServiceException e) {
									OHServiceExceptionUtil.showMessages(e);
								}
							} else {
								allRadio.setSelected(false);
								specificRadio.setSelected(true);
							}

						} else {
							if (mode.equals("update")) {
								try {
									allRadio.setSelected(true);
									jTableInventoryRow.setModel(new InventoryRowModel(true));

								} catch (OHServiceException e) {
									OHServiceExceptionUtil.showMessages(e);
								}
							} else {
								try {
									jTableInventoryRow.setModel(new InventoryRowModel());
								} catch (OHServiceException e) {
									OHServiceExceptionUtil.showMessages(e);
								}
							}
						}
						if (inventory != null && !inventory.getStatus().equals(InventoryStatus.draft.toString())) {
							inventory.setStatus(InventoryStatus.draft.toString());
						}	
						fireInventoryUpdated();
						code = null;
						adjustWidth();
						MessageDialog.info(null, "angal.invetory.allmedicaladdedsuccessfully.msg");
					} else {
						MessageDialog.info(null, "angal.inventory.youhavealreadyaddedallproduct.msg");
					}
				} catch (OHServiceException e) {
					OHServiceExceptionUtil.showMessages(e);
				}
			});
		}
		return allRadio;
	}

	private JLabel getDateInventoryLabel() {
		if (dateInventoryLabel == null) {
			dateInventoryLabel = new JLabel(MessageBundle.getMessage("angal.common.date.txt"));
		}
		return dateInventoryLabel;
	}

	private JTextField getCodeTextField() {
		if (codeTextField == null) {
			codeTextField = new JTextField();
			if (inventory != null) {
				codeTextField.setEnabled(false);
			} else {
				codeTextField.setEnabled(true);
			}
			codeTextField.setColumns(10);
			TextPrompt suggestion = new TextPrompt(MessageBundle.getMessage("angal.common.code.txt"), codeTextField, Show.FOCUS_LOST);
			suggestion.setFont(new Font("Tahoma", Font.PLAIN, 12));
			suggestion.setForeground(Color.GRAY);
			suggestion.setHorizontalAlignment(JLabel.CENTER);
			suggestion.changeAlpha(0.5f);
			suggestion.changeStyle(Font.BOLD + Font.ITALIC);
			codeTextField.addKeyListener(new KeyAdapter() {

				@Override
				public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER) {
						code = codeTextField.getText().trim();
						code = code.toLowerCase();
						try {
							addInventoryRow(code);
						} catch (OHServiceException e1) {
							OHServiceExceptionUtil.showMessages(e1);
						}
						if (inventory != null && !inventory.getStatus().equals(InventoryStatus.draft.toString())) {
							inventory.setStatus(InventoryStatus.draft.toString());
						}
						codeTextField.setText("");
					}
				}
			});
		}
		return codeTextField;
	}

	private List<MedicalInventoryRow> loadNewInventoryTable(String code, MedicalInventory inventory, boolean add) throws OHServiceException {
		List<MedicalInventoryRow> inventoryRowsList = new ArrayList<>();
		if (inventory != null) {
			int id = inventory.getId();
			inventoryRowsList = medicalInventoryRowManager.getMedicalInventoryRowByInventoryId(id);
			if (add) {
				inventoryRowsList = getMedicalInventoryRows(code);
			}
		} else {
			inventoryRowsList = getMedicalInventoryRows(code);
		}
		return inventoryRowsList;
	}

	private List<MedicalInventoryRow> getMedicalInventoryRows(String code) throws OHServiceException {
		List<MedicalInventoryRow> inventoryRowsList = new ArrayList<>();
		List<Medical> medicalList = new ArrayList<>();
		List<Lot> lots = null;
		Medical medical = null;
		MedicalInventoryRow inventoryRowTemp = null;
		if (code != null) {
			medical = medicalBrowsingManager.getMedicalByMedicalCode(code);
			if (medical != null) {
				medicalList.add(medical);
			} else {
				medical = chooseMedical(code);
				if (medical != null) {
					medicalList.add(medical);
				}
			}
		} else {
			medicalList = medicalBrowsingManager.getMedicals();
		}
		ListIterator<Medical> medicalListIterator = medicalList.listIterator();
		while (medicalListIterator.hasNext()) {
			Medical med = medicalListIterator.next();
			lots = movStockInsertingManager.getLotByMedical(med, false);
			double actualQty = med.getInqty() - med.getOutqty();
			if (lots.size() == 0) {
				inventoryRowTemp = new MedicalInventoryRow(0, actualQty, actualQty, null, med, null);
				if (!existInInventorySearchList(inventoryRowTemp)) {
					inventoryRowsList.add(inventoryRowTemp);
				}
			} else {
				ListIterator<Lot> lotListIterator = lots.listIterator();
				while (lotListIterator.hasNext()) {
					Lot lot = lotListIterator.next();
					inventoryRowTemp = new MedicalInventoryRow(0, lot.getMainStoreQuantity(), lot.getMainStoreQuantity(), null, med, lot);
					if (!existInInventorySearchList(inventoryRowTemp)) {
						inventoryRowsList.add(inventoryRowTemp);
					}
				}
			}
		}
		return inventoryRowsList;
	}
	private void addInventoryRow(String code) throws OHServiceException {
		List<MedicalInventoryRow> inventoryRowsList = new ArrayList<MedicalInventoryRow>();
		List<Medical> medicalList = new ArrayList<Medical>();
		List<Lot> lots = null;
		Medical medical = null;
		MedicalInventoryRow inventoryRowTemp = null;
		if (code != null) {
			medical = medicalBrowsingManager.getMedicalByMedicalCode(code);
			if (medical != null) {
				medicalList.add(medical);
			} else {
				medical = chooseMedical(code);
				if (medical != null) {
					medicalList.add(medical);
				}
			}
		} else {
			medicalList = medicalBrowsingManager.getMedicals();
		}
		int numberOfMedicalWithoutSameLotAdded = 0;
		Medical medicalWithLot = null;
		ListIterator<Medical> medicalListIterator = medicalList.listIterator();
		while (medicalListIterator.hasNext()) {
			Medical med = medicalListIterator.next();
			lots = movStockInsertingManager.getLotByMedical(med, false);
			if (lots.size() == 0) {
				inventoryRowTemp = new MedicalInventoryRow(0, 0.0, 0.0, null, med, null);
				if (!existInInventorySearchList(inventoryRowTemp)) {
					inventoryRowsList.add(inventoryRowTemp);
				} else {
					int info = MessageDialog.yesNo(null, "angal.inventory.productalreadyexist.msg", med.getDescription());
					if (info == JOptionPane.YES_OPTION) {
						inventoryRowsList.add(inventoryRowTemp);
					}
				}
			} else {
				medicalWithLot = med;
				ListIterator<Lot> lotListIterator = lots.listIterator();
				while (lotListIterator.hasNext()) {
					Lot lot = lotListIterator.next();
					inventoryRowTemp = new MedicalInventoryRow(0, lot.getMainStoreQuantity(), lot.getMainStoreQuantity(), null, med, lot);
					if (!existInInventorySearchList(inventoryRowTemp)) {
						inventoryRowsList.add(inventoryRowTemp);
						numberOfMedicalWithoutSameLotAdded = numberOfMedicalWithoutSameLotAdded + 1;
					}
				}
			}
		}
		if (medicalWithLot != null && numberOfMedicalWithoutSameLotAdded == 0) {
			int info = MessageDialog.yesNo(null, "angal.inventory.productalreadyexist.msg", medicalWithLot.getDescription());
			if (info == JOptionPane.YES_OPTION) {
				inventoryRowTemp = new MedicalInventoryRow(0, 0.0, 0.0, null, medicalWithLot, null);
				inventoryRowsList.add(inventoryRowTemp);
			}
		}
		for (MedicalInventoryRow inventoryRow : inventoryRowsList) {
			addMedInRowInInventorySearchList(inventoryRow);
		}
		jTableInventoryRow.updateUI();
	}

	private Medical chooseMedical(String text) throws OHServiceException {
		Map<String, Medical> medicalMap;
		List<Medical> medicals = medicalBrowsingManager.getMedicals();
		medicalMap = new HashMap<String, Medical>();
		for (Medical med : medicals) {
			String key = med.getProdCode().toLowerCase();
			key = med.getCode().toString().toLowerCase();
			medicalMap.put(key, med);
		}
		ArrayList<Medical> medList = new ArrayList<Medical>();
		for (Medical aMed : medicalMap.values()) {
			if (NormalizeString.normalizeContains(aMed.getDescription().toLowerCase(), text)) {
				medList.add(aMed);
			}
		}
		Collections.sort(medList);
		Medical med = null;
		if (!medList.isEmpty()) {
			MedicalPicker framas = new MedicalPicker(new StockMedModel(medList), medList);
			framas.setSize(300, 400);
			JDialog dialog = new JDialog();
			dialog.setLocationRelativeTo(null);
			dialog.setSize(600, 350);
			dialog.setLocationRelativeTo(null);
			dialog.setModal(true);
			dialog.setTitle(MessageBundle.getMessage("angal.medicalstock.multiplecharging.selectmedical.title"));
			framas.setParentFrame(dialog);
			dialog.setContentPane(framas);
			dialog.setVisible(true);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			med = framas.getSelectedMedical();
			return med;
		}
		return null;
	}

	public EventListenerList getInventoryListeners() {
		return InventoryListeners;
	}

	public void setInventoryListeners(EventListenerList inventoryListeners) {
		InventoryListeners = inventoryListeners;
	}

	private JLabel getReferenceLabel() {
		if (referenceLabel == null) {
			referenceLabel = new JLabel(MessageBundle.getMessage("angal.common.reference.label"));
		}
		return referenceLabel;
	}

	private JLabel getStatusLabel() {
		if (statusLabel == null) {
			if (inventory == null) {
				String currentStatus = InventoryStatus.draft.toString().toUpperCase();
				statusLabel = new JLabel(currentStatus);
				statusLabel.setForeground(Color.GRAY);
			} else {
				String currentStatus = inventory.getStatus().toUpperCase();
				statusLabel = new JLabel(currentStatus);
				if (currentStatus.equalsIgnoreCase(InventoryStatus.draft.toString())) {
					statusLabel.setForeground(Color.GRAY);
				}
				if (currentStatus.equalsIgnoreCase(InventoryStatus.validated.toString())) {
					statusLabel.setForeground(Color.BLUE);
				}
				if (currentStatus.equalsIgnoreCase(InventoryStatus.canceled.toString())) {
					statusLabel.setForeground(Color.RED);
				}
				if (currentStatus.equalsIgnoreCase(InventoryStatus.done.toString())) {
					statusLabel.setForeground(Color.GREEN);
				}
			}
			statusLabel.setFont(new Font(statusLabel.getFont().getName(), Font.BOLD, statusLabel.getFont().getSize() + 8));
		}
		return statusLabel;
	}

	private JLabel getChargeLabel() {
		if (chargeTypeLabel == null) {
			chargeTypeLabel = new JLabel(MessageBundle.getMessage("angal.inventory.chargetype.label"));
		}
		return chargeTypeLabel;
	}

	private JLabel getSupplierLabel() {
		if (supplierLabel == null) {
			supplierLabel = new JLabel(MessageBundle.getMessage("angal.inventory.supplier.label"));
		}
		return supplierLabel;
	}

	private JLabel getDischargeLabel() {
		if (dischargeTypeLabel == null) {
			dischargeTypeLabel = new JLabel(MessageBundle.getMessage("angal.inventory.dischargetype.label"));
		}
		return dischargeTypeLabel;
	}

	private JLabel getDestinationLabel() {
		if (destinationLabel == null) {
			destinationLabel = new JLabel(MessageBundle.getMessage("angal.inventory.destination.label"));
		}
		return destinationLabel;
	}

	private JComboBox<MovementType> getJComboCharge() {
		MovementType movementSelected = null;
		if (chargeCombo == null) {
			chargeCombo = new JComboBox<MovementType>();
			try {
				List<MovementType> movementTypes = movTypeManager.getMedicalDsrStockMovementType();
				chargeCombo.addItem(null);
				for (MovementType movType : movementTypes) {
					if (movType.getType().equals("+")) {
						chargeCombo.addItem(movType);
						if (inventory != null && movType.getCode().equals(inventory.getChargeType())) {
							movementSelected = movType;
						}
					}
				}
			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
			}
			if (inventory != null) {
				chargeCombo.setSelectedItem(movementSelected);
				chargeType = movementSelected;
			}
			chargeCombo.addActionListener(actionEvent -> {
				chargeType = (MovementType) chargeCombo.getSelectedItem();
			});
		}
		return chargeCombo;
	}

	private JComboBox<MovementType> getJComboDischarge() {
		MovementType movementSelected = null;
		if (dischargeCombo == null) {
			dischargeCombo = new JComboBox<MovementType>();
			try {
				List<MovementType> movementTypes = movTypeManager.getMedicalDsrStockMovementType();
				dischargeCombo.addItem(null);
				for (MovementType movType : movementTypes) {
					if (movType.getType().equals("-")) {
						dischargeCombo.addItem(movType);
						if (inventory != null && movType.getCode().equals(inventory.getDischargeType())) {
							movementSelected = movType;
						}
					}
				}
			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
			}
			if (inventory != null) {
				dischargeCombo.setSelectedItem(movementSelected);
				dischargeType = movementSelected;
			}
			dischargeCombo.addActionListener(actionEvent -> {
				dischargeType = (MovementType) dischargeCombo.getSelectedItem();
			});
		}
		return dischargeCombo;
	}

	private JComboBox<Supplier> getJComboSupplier() {
		Supplier supplierSelected = null;
		if (supplierCombo == null) {
			supplierCombo = new JComboBox<Supplier>();
			try {
				List<Supplier> suppliers = supplierManager.getList();
				supplierCombo.addItem(null);
				for (Supplier supplier : suppliers) {
					supplierCombo.addItem(supplier);
					if (inventory != null && supplier.getSupId() == inventory.getSupplier()) {
						supplierSelected = supplier;
					}
				}
			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
			}
			if (inventory != null) {
				supplierCombo.setSelectedItem(supplierSelected);
				supplier = supplierSelected;
			}
			supplierCombo.addActionListener(actionEvent -> {
				supplier = (Supplier) supplierCombo.getSelectedItem();
			});
		}
		return supplierCombo;
	}

	private JComboBox<Ward> getJComboDestination() {
		Ward destinationSelected = null;
		if (destinationCombo == null) {
			destinationCombo = new JComboBox<Ward>();
			try {
				List<Ward> wards = wardManager.getWards();
				destinationCombo.addItem(null);
				for (Ward ward : wards) {
					destinationCombo.addItem(ward);
					if (inventory != null && ward.getCode().equals(inventory.getDestination())) {
						destinationSelected = ward;
					}
				}
			} catch (OHServiceException e) {
				OHServiceExceptionUtil.showMessages(e);
			}
			if (inventory != null) {
				destinationCombo.setSelectedItem(destinationSelected);
				destination = destinationSelected;
			}
			destinationCombo.addActionListener(actionEvent -> {
				destination = (Ward) destinationCombo.getSelectedItem();
			});
		}
		return destinationCombo;
	}

	private JTextField getReferenceTextField() {
		if (referenceTextField == null) {
			referenceTextField = new JTextField();
			referenceTextField.setColumns(10);
			if (inventory != null && !mode.equals("new")) {
				referenceTextField.setText(inventory.getInventoryReference());
			}
		}
		return referenceTextField;
	}

	private int getPosition(MedicalInventoryRow inventoryRow) {
		int position = -1;
		int i = 0;
		for (MedicalInventoryRow invR : inventoryRowSearchList) {
			if (invR.getMedical().getCode().equals(inventoryRow.getMedical().getCode())) {
				position = i;
			}
			i = i + 1;
		}
		return position;
	}

	private boolean existInInventorySearchList(MedicalInventoryRow inventoryRow) {
		boolean found = false;
		List<MedicalInventoryRow> invRows = inventoryRowSearchList.stream()
						.filter(inv -> inv.getMedical().getCode().equals(inventoryRow.getMedical().getCode())).collect(Collectors.toList());
		if (invRows.size() > 0) {
			for (MedicalInventoryRow invR : invRows) {
				if (inventoryRow.getLot() != null && invR.getLot() != null) {
					if (inventoryRow.getLot().getCode().equals(invR.getLot().getCode())) {
						found = true;
						break;
					}
				} else {
					if (invR.getLot() == null && inventoryRow.getLot() == null) {
						found = true;
						break;
					}
				}
			}
		}

		return found;
	}
	private void addMedInRowInInventorySearchList(MedicalInventoryRow inventoryRow) {
		int position = getPosition(inventoryRow);
		if (position == -1) {
			position = inventoryRowSearchList.size();
			inventoryRowSearchList.add(position, inventoryRow);
		} else {
			inventoryRowSearchList.add(position + 1, inventoryRow);
		}
		if (inventoryRow.getId() == 0) {
			inventoryRowListAdded.add(inventoryRow);
		}
	}
	private void resetVariable() {
		inventoryRowsToDelete.clear();
		lotsDeleted.clear();
		inventoryRowListAdded.clear();
		lotsSaved.clear();
	}
	
	private boolean checkParameters(String wardCode, String chargeCode, String dischargeCode, Integer suplierId, String reference, LocalDateTime date) {
		if (!lotsSaved.isEmpty() || !inventoryRowListAdded.isEmpty() || !lotsDeleted.isEmpty() || !inventoryRowsToDelete.isEmpty()
							|| (destination != null && !destination.getCode().equals(wardCode))
							|| (chargeType != null && !chargeType.getCode().equals(chargeCode))
							|| (dischargeType != null && !dischargeType.getCode().equals(dischargeCode))
							|| (supplier != null && !supplier.getSupId().equals(suplierId)) || (destination == null && wardCode != null)
							|| (chargeType == null && chargeCode != null) || (dischargeType == null && dischargeCode != null)
							|| (supplier == null && suplierId != null) || (reference != null && !reference.equals(newReference))
							|| !date.toLocalDate().equals(dateInventory.toLocalDate())) {
			return true;
		}
		return false;
	}
	
	private  boolean areAllMedicalsInInventory() throws OHServiceException {
		List<Medical> medicals = medicalBrowsingManager.getMedicals();
		Set<Medical> inventorySet = new HashSet<>();
		for (MedicalInventoryRow row : inventoryRowSearchList) {
			inventorySet.add(row.getMedical());
		}
		if (medicals.size() == inventorySet.size()) {
			return true;
		}
		return false;
	}
}