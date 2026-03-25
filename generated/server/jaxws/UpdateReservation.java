
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "updateReservation", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "updateReservation", namespace = "http://server/", propOrder = {
    "customerID",
    "vehicleID",
    "newStartDate",
    "newEndDate"
})
public class UpdateReservation {

    @XmlElement(name = "customerID", namespace = "")
    private String customerID;
    @XmlElement(name = "vehicleID", namespace = "")
    private String vehicleID;
    @XmlElement(name = "newStartDate", namespace = "")
    private String newStartDate;
    @XmlElement(name = "newEndDate", namespace = "")
    private String newEndDate;

    /**
     * 
     * @return
     *     returns String
     */
    public String getCustomerID() {
        return this.customerID;
    }

    /**
     * 
     * @param customerID
     *     the value for the customerID property
     */
    public void setCustomerID(String customerID) {
        this.customerID = customerID;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getVehicleID() {
        return this.vehicleID;
    }

    /**
     * 
     * @param vehicleID
     *     the value for the vehicleID property
     */
    public void setVehicleID(String vehicleID) {
        this.vehicleID = vehicleID;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getNewStartDate() {
        return this.newStartDate;
    }

    /**
     * 
     * @param newStartDate
     *     the value for the newStartDate property
     */
    public void setNewStartDate(String newStartDate) {
        this.newStartDate = newStartDate;
    }

    /**
     * 
     * @return
     *     returns String
     */
    public String getNewEndDate() {
        return this.newEndDate;
    }

    /**
     * 
     * @param newEndDate
     *     the value for the newEndDate property
     */
    public void setNewEndDate(String newEndDate) {
        this.newEndDate = newEndDate;
    }

}
