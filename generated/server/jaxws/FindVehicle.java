
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "findVehicle", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "findVehicle", namespace = "http://server/", propOrder = {
    "customerID",
    "vehicleType"
})
public class FindVehicle {

    @XmlElement(name = "customerID", namespace = "")
    private String customerID;
    @XmlElement(name = "vehicleType", namespace = "")
    private String vehicleType;

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
    public String getVehicleType() {
        return this.vehicleType;
    }

    /**
     * 
     * @param vehicleType
     *     the value for the vehicleType property
     */
    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

}
